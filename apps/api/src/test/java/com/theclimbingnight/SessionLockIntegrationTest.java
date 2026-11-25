package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.entity.RefreshToken;
import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.auth.service.AuthService;
import com.theclimbingnight.auth.service.RefreshTokens;
import com.theclimbingnight.auth.service.TokenPair;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.auth.social.SocialTokenVerifier;
import com.theclimbingnight.auth.social.SocialTokenVerifiers;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** 먼저 시작한 트랜잭션이 커밋하기 전에 다른 요청이 끼어드는 경우. 실제 행 잠금이 필요해 DB로 확인한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:session-lock;MODE=MySQL;LOCK_TIMEOUT=10000")
class SessionLockIntegrationTest {

	/** 끼어든 요청이 잠금에 걸리거나 끝날 때까지 기다리는 시간. 짧아도 올바른 코드는 실패하지 않는다. */
	private static final long INTERLEAVE_MILLIS = 1_000;

	@MockitoBean SocialTokenVerifiers verifiers;

	@Autowired AuthService authService;
	@Autowired UserService userService;
	@Autowired UserRepository userRepository;
	@Autowired AuthIdentityRepository authIdentityRepository;
	@Autowired RefreshTokenRepository refreshTokenRepository;
	@Autowired TransactionTemplate transactionTemplate;
	@Autowired Clock clock;

	/** first를 커밋하지 않은 채 other를 다른 스레드에서 부르고, first를 커밋한 뒤 other의 오류 코드를 준다. 성공하면 null. */
	private ErrorCode interleave(Runnable first, Supplier<?> other) throws Exception {
		CompletableFuture<?> future = transactionTemplate.execute(status -> {
			first.run();
			CompletableFuture<?> running = CompletableFuture.supplyAsync(other);
			try {
				running.get(INTERLEAVE_MILLIS, TimeUnit.MILLISECONDS);
			} catch (TimeoutException | ExecutionException e) {
				// 잠금을 기다리거나 이미 실패했다. 결과는 커밋 뒤에 본다.
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
			return running;
		});
		try {
			future.get(20, TimeUnit.SECONDS);
			return null;
		} catch (ExecutionException e) {
			assertThat(e.getCause()).isInstanceOf(BusinessException.class);
			return ((BusinessException) e.getCause()).getErrorCode();
		}
	}

	private RefreshToken find(String token) {
		return refreshTokenRepository.findByTokenHash(RefreshTokens.hash(token)).orElseThrow();
	}

	@Test
	void 재사용을_감지하면_동시에_갱신_중인_세션도_폐기한다() throws Exception {
		// given: 오래전에 폐기된 토큰과 지금 쓰는 토큰
		User user = userRepository.save(User.create("재사용감지"));
		Instant now = clock.instant();
		RefreshToken stolen = RefreshToken.issue(user.getId(), RefreshTokens.hash("stolen"),
				now.plus(Duration.ofDays(1)));
		stolen.revoke(now.minus(Duration.ofMinutes(5)));
		refreshTokenRepository.save(stolen);
		TokenPair current = authService.issueTokens(user);
		TokenPair[] renewed = new TokenPair[1];

		// when
		ErrorCode code = interleave(() -> renewed[0] = authService.refresh(current.refreshToken()),
				() -> authService.refresh("stolen"));

		// then
		assertThat(code).isEqualTo(ErrorCode.AUTH_REQUIRED);
		assertThat(find(renewed[0].refreshToken()).getRevokedAt()).isNotNull();
	}

	@Test
	void 탈퇴하는_동안_로그인하면_401이고_토큰을_남기지_않는다() throws Exception {
		// given
		User user = userRepository.save(User.create("탈퇴중로그인"));
		authIdentityRepository.save(AuthIdentity.link(user, SocialProvider.KAKAO, "session-lock"));
		SocialTokenVerifier verifier = Mockito.mock(SocialTokenVerifier.class);
		BDDMockito.given(verifiers.get(SocialProvider.KAKAO)).willReturn(verifier);
		BDDMockito.given(verifier.verify("social"))
				.willReturn(new SocialIdentity(SocialProvider.KAKAO, "session-lock"));

		// when
		ErrorCode code = interleave(() -> {
			userService.withdraw(user.getId());
			userRepository.flush();
		}, () -> authService.login(SocialProvider.KAKAO, "social"));

		// then
		assertThat(code).isEqualTo(ErrorCode.AUTH_REQUIRED);
		assertThat(refreshTokenRepository.findAll())
				.noneMatch(token -> token.getUserId() == user.getId() && token.getRevokedAt() == null);
	}

	private Void logout(String refreshToken) {
		authService.logout(refreshToken, null);
		return null;
	}

	@Test
	void 갱신하는_동안_로그아웃하면_갱신된_세션도_폐기한다() throws Exception {
		// given
		User user = userRepository.save(User.create("갱신중로그아웃"));
		TokenPair current = authService.issueTokens(user);
		TokenPair[] renewed = new TokenPair[1];

		// when
		ErrorCode code = interleave(() -> renewed[0] = authService.refresh(current.refreshToken()),
				() -> logout(current.refreshToken()));

		// then
		assertThat(code).isNull();
		assertThat(find(renewed[0].refreshToken()).getRevokedAt()).isNotNull();
	}

	@Test
	void 로그아웃하는_동안_갱신하면_401이다() throws Exception {
		// given
		User user = userRepository.save(User.create("로그아웃중갱신"));
		TokenPair current = authService.issueTokens(user);

		// when
		ErrorCode code = interleave(() -> {
			logout(current.refreshToken());
			refreshTokenRepository.flush();
		}, () -> authService.refresh(current.refreshToken()));

		// then
		assertThat(code).isEqualTo(ErrorCode.AUTH_REQUIRED);
	}

	@Test
	void 여러_번_갱신된_뒤_옛_토큰으로_로그아웃해도_마지막_세션을_폐기한다() {
		// given
		User user = userRepository.save(User.create("여러번갱신"));
		TokenPair first = authService.issueTokens(user);
		TokenPair second = authService.refresh(first.refreshToken());
		TokenPair third = authService.refresh(second.refreshToken());

		// when
		logout(second.refreshToken());

		// then
		assertThat(find(third.refreshToken()).getRevokedAt()).isNotNull();
	}

	@Test
	void 옛_토큰으로_로그아웃해도_다른_기기의_세션은_남는다() {
		// given
		User user = userRepository.save(User.create("다른기기"));
		TokenPair first = authService.issueTokens(user);
		TokenPair otherDevice = authService.issueTokens(user);
		authService.refresh(first.refreshToken());

		// when
		logout(first.refreshToken());

		// then
		assertThat(find(otherDevice.refreshToken()).getRevokedAt()).isNull();
	}

	@Test
	void 늦게_다시_온_로그아웃은_그_뒤의_로그인을_폐기하지_않는다() {
		// given
		User user = userRepository.save(User.create("늦은로그아웃"));
		TokenPair first = authService.issueTokens(user);
		logout(first.refreshToken());
		TokenPair again = authService.issueTokens(user);

		// when
		logout(first.refreshToken());

		// then
		assertThat(find(again.refreshToken()).getRevokedAt()).isNull();
	}

}
