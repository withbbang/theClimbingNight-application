package com.theclimbingnight.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.entity.RefreshToken;
import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.auth.social.SocialTokenVerifier;
import com.theclimbingnight.auth.social.SocialTokenVerifiers;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.NicknamePolicy;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

	private static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnopqrstuv";
	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
	private static final SocialIdentity KAKAO_42 = new SocialIdentity(SocialProvider.KAKAO, "42");

	@Mock UserRepository userRepository;
	@Mock AuthIdentityRepository authIdentityRepository;
	@Mock RefreshTokenRepository refreshTokenRepository;
	@Mock SocialTokenVerifiers verifiers;
	@Mock SocialTokenVerifier verifier;
	@Mock com.theclimbingnight.push.service.PushTokenService pushTokenService;

	private MutableClock clock;
	private JwtTokenService jwtTokenService;
	private AuthService service;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(START);
		AuthProperties props = new AuthProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(30),
				Duration.ofMinutes(10), null, null, null, null);
		jwtTokenService = new JwtTokenService(props, clock);
		service = new AuthService(userRepository, authIdentityRepository, refreshTokenRepository, verifiers,
				jwtTokenService, new RefreshTokens(new SecureRandom()), new NicknamePolicy(), props, clock,
				new TransactionTemplate(org.mockito.Mockito.mock(PlatformTransactionManager.class)), pushTokenService);
		given(verifiers.get(SocialProvider.KAKAO)).willReturn(verifier);
		given(verifier.verify("social-token")).willReturn(KAKAO_42);
		given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> {
			User u = inv.getArgument(0);
			ReflectionTestUtils.setField(u, "id", 7L);
			return u;
		});
		given(authIdentityRepository.saveAndFlush(any(AuthIdentity.class))).willAnswer(inv -> inv.getArgument(0));
	}

	private User user(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private TokenPair issued(User user) {
		return service.issueTokens(user);
	}

	private RefreshToken savedRefresh() {
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		return captor.getValue();
	}

	private void assertCode(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(code));
	}

	@Test
	void 연결된_소셜계정이면_토큰을_발급한다() {
		// given
		User user = user(7L);
		given(authIdentityRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "42"))
				.willReturn(Optional.of(AuthIdentity.link(user, SocialProvider.KAKAO, "42")));

		// when
		LoginResult result = service.login(SocialProvider.KAKAO, "social-token");

		// then
		assertThat(result).isInstanceOf(LoginResult.LoggedIn.class);
		TokenPair tokens = ((LoginResult.LoggedIn) result).tokens();
		assertThat(jwtTokenService.parseAccessToken(tokens.accessToken())).isEqualTo(7L);
		assertThat(tokens.accessTokenExpiresAt()).isEqualTo(START.plus(Duration.ofMinutes(30)));
		RefreshToken saved = savedRefresh();
		assertThat(saved.getTokenHash()).isEqualTo(RefreshTokens.hash(tokens.refreshToken())).hasSize(64);
		assertThat(saved.getTokenHash()).isNotEqualTo(tokens.refreshToken());
		assertThat(saved.getUserId()).isEqualTo(7L);
		assertThat(saved.getExpiresAt()).isEqualTo(START.plus(Duration.ofDays(30)));
	}

	@Test
	void 처음_보는_소셜계정이면_가입_토큰을_돌려준다() {
		// given
		given(authIdentityRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "42"))
				.willReturn(Optional.empty());

		// when
		LoginResult result = service.login(SocialProvider.KAKAO, "social-token");

		// then
		assertThat(result).isInstanceOf(LoginResult.SignupRequired.class);
		String signupToken = ((LoginResult.SignupRequired) result).signupToken();
		assertThat(jwtTokenService.parseSignupToken(signupToken)).isEqualTo(KAKAO_42);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void 탈퇴한_사용자에_연결된_소셜계정이면_401이다() {
		// given
		User user = user(7L);
		user.withdraw(START);
		given(authIdentityRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "42"))
				.willReturn(Optional.of(AuthIdentity.link(user, SocialProvider.KAKAO, "42")));

		// when, then
		assertCode(() -> service.login(SocialProvider.KAKAO, "social-token"), ErrorCode.AUTH_REQUIRED);
	}

	@Test
	void 가입하면_USER로_만들고_소셜계정을_연결한다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);

		// when
		TokenPair tokens = service.signup(signupToken, "  climber ");

		// then
		assertThat(tokens.user().getNickname()).isEqualTo("climber");
		assertThat(tokens.user().getRole()).isEqualTo(Role.USER);
		ArgumentCaptor<AuthIdentity> captor = ArgumentCaptor.forClass(AuthIdentity.class);
		verify(authIdentityRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(captor.getValue().getProviderUserId()).isEqualTo("42");
		assertThat(jwtTokenService.parseAccessToken(tokens.accessToken())).isEqualTo(7L);
	}

	@Test
	void 가입_때_닉네임이_사용중이면_409다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		given(userRepository.existsByNicknameIgnoreCase("climber")).willReturn(true);

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.USER_NICKNAME_TAKEN);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 대소문자만_다른_닉네임도_사용중이면_409다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		given(userRepository.existsByNicknameIgnoreCase("climber")).willReturn(true);

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.USER_NICKNAME_TAKEN);
		verify(userRepository).existsByNicknameIgnoreCase("climber");
		given(userRepository.existsByNicknameIgnoreCase("Climber")).willReturn(true);
		assertCode(() -> service.signup(signupToken, "Climber"), ErrorCode.USER_NICKNAME_TAKEN);
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		java.sql.SQLException sql = new java.sql.SQLException("Duplicate entry for key '" + constraintName + "'");
		return new DataIntegrityViolationException("could not execute statement",
				new org.hibernate.exception.ConstraintViolationException("could not execute statement", sql,
						constraintName));
	}

	@Test
	void 동시_가입으로_닉네임_유니크_제약이_깨지면_409다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		given(userRepository.saveAndFlush(any(User.class))).willThrow(violation("uk_users_nickname"));

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.USER_NICKNAME_TAKEN);
		verify(authIdentityRepository, org.mockito.Mockito.times(1))
				.findByProviderAndProviderUserId(SocialProvider.KAKAO, "42");
	}

	@Test
	void 가입_중_같은_소셜계정이_먼저_저장되면_409다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		given(authIdentityRepository.saveAndFlush(any(AuthIdentity.class)))
				.willThrow(violation("uk_auth_identities_provider"));

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.AUTH_ALREADY_REGISTERED);
		verify(authIdentityRepository, org.mockito.Mockito.times(1))
				.findByProviderAndProviderUserId(SocialProvider.KAKAO, "42");
	}

	@Test
	void 제약_이름이_메시지에만_있어도_구분한다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		given(authIdentityRepository.saveAndFlush(any(AuthIdentity.class))).willThrow(
				new DataIntegrityViolationException("x", new java.sql.SQLException(
						"Unique index or primary key violation: \"PUBLIC.UK_AUTH_IDENTITIES_PROVIDER_INDEX_3\"")));

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.AUTH_ALREADY_REGISTERED);
	}

	@Test
	void 알_수_없는_제약_위반은_그대로_던진다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		DataIntegrityViolationException unknown = violation("uk_something_else");
		given(userRepository.saveAndFlush(any(User.class))).willThrow(unknown);

		// when, then
		assertThatThrownBy(() -> service.signup(signupToken, "climber")).isSameAs(unknown);
	}

	@Test
	void 이미_연결된_소셜계정으로_가입하면_409다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		given(authIdentityRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "42"))
				.willReturn(Optional.of(AuthIdentity.link(user(1L), SocialProvider.KAKAO, "42")));

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.AUTH_ALREADY_REGISTERED);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 만료된_가입_토큰이면_400이다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);
		clock.advance(Duration.ofMinutes(10).plusSeconds(1));

		// when, then
		assertCode(() -> service.signup(signupToken, "climber"), ErrorCode.AUTH_SIGNUP_EXPIRED);
	}

	@Test
	void 닉네임_규칙에_어긋나면_400이다() {
		// given
		String signupToken = jwtTokenService.issueSignupToken(KAKAO_42);

		// when, then
		assertCode(() -> service.signup(signupToken, "a b"), ErrorCode.USER_NICKNAME_INVALID);
	}

	@Test
	void 리프레시하면_새_토큰을_주고_이전_토큰은_폐기한다() {
		// given
		User user = user(7L);
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		ReflectionTestUtils.setField(old, "id", 99L);
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user));
		given(refreshTokenRepository.revokeIfActive(any(Long.class), any(Instant.class))).willReturn(1);

		// when
		TokenPair tokens = service.refresh("old-token");

		// then
		InOrder order = inOrder(userRepository, refreshTokenRepository);
		order.verify(userRepository).findByIdForUpdate(7L);
		order.verify(refreshTokenRepository).revokeIfActive(99L, START);
		assertThat(tokens.refreshToken()).isNotEqualTo("old-token");
		assertThat(savedRefresh().getTokenHash()).isEqualTo(RefreshTokens.hash(tokens.refreshToken()));
	}

	@Test
	void 동시에_다른_요청이_먼저_폐기했으면_401이고_새_토큰을_저장하지_않는다() {
		// given: 조회 시점에는 유효했지만 조건부 폐기가 0건이다
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		ReflectionTestUtils.setField(old, "id", 99L);
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user(7L)));
		given(refreshTokenRepository.revokeIfActive(any(Long.class), any(Instant.class))).willReturn(0);

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void 이미_사용한_리프레시_토큰이면_401이다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		old.revoke(START);
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user(7L)));

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
	}

	private RefreshToken revokedToken(Instant revokedAt) {
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		ReflectionTestUtils.setField(old, "id", 99L);
		old.revoke(revokedAt);
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user(7L)));
		return old;
	}

	@Test
	void 유예_시간이_지난_뒤_폐기된_토큰을_재사용하면_회원의_모든_토큰을_폐기하고_401이다() {
		// given
		revokedToken(START);
		clock.advance(Duration.ofSeconds(31));

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
		verify(refreshTokenRepository).revokeAllActiveByUserId(7L, START.plusSeconds(31));
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void 유예_시간_안에_폐기된_토큰을_재사용하면_전체_폐기_없이_401이다() {
		// given
		revokedToken(START);
		clock.advance(Duration.ofSeconds(29));

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(Long.class), any(Instant.class));
	}

	@Test
	void 동시_경합으로_조건부_폐기가_0건이면_전체_폐기_없이_401이다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		ReflectionTestUtils.setField(old, "id", 99L);
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user(7L)));
		given(refreshTokenRepository.revokeIfActive(any(Long.class), any(Instant.class))).willReturn(0);

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(Long.class), any(Instant.class));
	}

	@Test
	void 만료된_뒤_폐기된_토큰을_재사용해도_전체_폐기는_하지_않는다() {
		// given
		revokedToken(START);
		clock.advance(Duration.ofDays(31));

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(Long.class), any(Instant.class));
	}

	@Test
	void 없는_리프레시_토큰이면_401이다() {
		// given
		given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

		// when, then
		assertCode(() -> service.refresh("nothing"), ErrorCode.AUTH_REQUIRED);
	}

	@Test
	void 만료된_리프레시_토큰이면_401이다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user(7L)));
		clock.advance(Duration.ofDays(30).plusSeconds(1));

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
	}

	@Test
	void 탈퇴한_사용자의_리프레시_토큰이면_401이다() {
		// given
		User user = user(7L);
		user.withdraw(START);
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user));

		// when, then
		assertCode(() -> service.refresh("old-token"), ErrorCode.AUTH_REQUIRED);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void 로그아웃하면_리프레시_토큰이_폐기된다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", null);

		// then
		assertThat(old.getRevokedAt()).isEqualTo(START);
	}

	@Test
	void 없는_리프레시_토큰으로_로그아웃해도_정상_종료한다() {
		// given
		given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

		// when, then
		service.logout("nothing", "ExponentPushToken[abc]");
		verify(pushTokenService, never()).deleteOwned(org.mockito.ArgumentMatchers.anyLong(), any());
	}

	@Test
	void 로그아웃하면_내_토큰만_지운다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", "ExponentPushToken[abc]");

		// then
		verify(pushTokenService).deleteOwned(7L, "ExponentPushToken[abc]");
		assertThat(old.getRevokedAt()).isEqualTo(START);
	}

	@Test
	void 푸시_토큰은_리프레시_토큰_폐기를_반영한_뒤에_지운다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", "ExponentPushToken[abc]");

		// then
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(refreshTokenRepository, pushTokenService);
		order.verify(refreshTokenRepository).flush();
		order.verify(pushTokenService).deleteOwned(7L, "ExponentPushToken[abc]");
	}

	@Test
	void 이미_폐기된_리프레시_토큰의_로그아웃은_폐기_시각_이전의_푸시_토큰만_지운다() {
		// given
		Instant revokedAt = START.minusSeconds(60);
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		old.revoke(revokedAt);
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", "ExponentPushToken[abc]");

		// then
		verify(pushTokenService).deleteOwnedIfNotSeenSince(7L, "ExponentPushToken[abc]", revokedAt);
		verify(pushTokenService, never()).deleteOwned(org.mockito.ArgumentMatchers.anyLong(), any());
		assertThat(old.getRevokedAt()).isEqualTo(revokedAt);
	}

	@Test
	void 폐기되지_않은_리프레시_토큰의_로그아웃은_조건_없이_내_토큰을_지운다() {
		// given: 만료됐지만 폐기되지 않은 토큰
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.minusSeconds(1));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", "ExponentPushToken[abc]");

		// then
		verify(pushTokenService).deleteOwned(7L, "ExponentPushToken[abc]");
		verify(pushTokenService, never()).deleteOwnedIfNotSeenSince(org.mockito.ArgumentMatchers.anyLong(), any(), any());
		assertThat(old.getRevokedAt()).isEqualTo(START);
	}

	@Test
	void 형식이_다른_푸시_토큰은_지우지_않고_로그아웃한다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", "토큰[한글]");

		// then
		assertThat(old.getRevokedAt()).isEqualTo(START);
		verify(pushTokenService, never()).deleteOwned(org.mockito.ArgumentMatchers.anyLong(), any());
	}

	@Test
	void 푸시_토큰_없이도_로그아웃된다() {
		// given
		RefreshToken old = RefreshToken.issue(7L, RefreshTokens.hash("old-token"), START.plus(Duration.ofDays(30)));
		given(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old-token"))).willReturn(Optional.of(old));

		// when
		service.logout("old-token", null);

		// then
		assertThat(old.getRevokedAt()).isEqualTo(START);
		verify(pushTokenService, never()).deleteOwned(org.mockito.ArgumentMatchers.anyLong(), any());
	}

	@Test
	void 리프레시_토큰은_43자_base64url이고_매번_다르다() {
		// given
		RefreshTokens tokens = new RefreshTokens(new SecureRandom());

		// when
		String a = tokens.newToken();
		String b = tokens.newToken();

		// then
		assertThat(a).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(b);
		assertThat(RefreshTokens.hash(a)).matches("[0-9a-f]{64}");
	}

}
