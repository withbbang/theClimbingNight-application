package com.theclimbingnight.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

	private static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnopqrstuv";
	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	private MutableClock clock;
	private JwtTokenService service;

	private static AuthProperties props(String secret) {
		return new AuthProperties(secret, Duration.ofMinutes(30), Duration.ofDays(30), Duration.ofMinutes(10),
				null, null, null, null);
	}

	@BeforeEach
	void setUp() {
		clock = new MutableClock(START);
		service = new JwtTokenService(props(SECRET), clock);
	}

	@Test
	void 발급한_액세스_토큰에서_사용자ID를_꺼낸다() {
		// given
		String token = service.issueAccessToken(42L).token();

		// when
		long userId = service.parseAccessToken(token);

		// then
		assertThat(userId).isEqualTo(42L);
		assertThat(service.issueAccessToken(42L).expiresAt()).isEqualTo(START.plus(Duration.ofMinutes(30)));
	}

	@Test
	void 만료_시각은_JWT_exp_클레임과_같고_초_단위로_잘린다() throws Exception {
		// given
		MutableClock millisClock = new MutableClock(Instant.parse("2026-01-01T00:00:00.789Z"));
		JwtTokenService millisService = new JwtTokenService(props(SECRET), millisClock);

		// when
		IssuedAccessToken issued = millisService.issueAccessToken(42L);

		// then
		Instant exp = com.nimbusds.jwt.SignedJWT.parse(issued.token()).getJWTClaimsSet().getExpirationTime()
				.toInstant();
		assertThat(issued.expiresAt()).isEqualTo(exp);
		assertThat(issued.expiresAt()).isEqualTo(Instant.parse("2026-01-01T00:30:00Z"));
	}

	@Test
	void 액세스_토큰은_30분_직전까지_유효하다() {
		// given
		String token = service.issueAccessToken(42L).token();

		// when
		clock.advance(Duration.ofMinutes(29).plusSeconds(59));

		// then
		assertThat(service.parseAccessToken(token)).isEqualTo(42L);
	}

	@Test
	void 액세스_토큰은_30분이_지나면_401이다() {
		// given
		String token = service.issueAccessToken(42L).token();

		// when
		clock.advance(Duration.ofMinutes(30).plusSeconds(1));

		// then
		assertThatThrownBy(() -> service.parseAccessToken(token))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
	}

	@Test
	void 가입_토큰은_액세스_토큰으로_쓸_수_없다() {
		// given
		String signup = service.issueSignupToken(new SocialIdentity(SocialProvider.KAKAO, "k-1"));

		// when / then
		assertThatThrownBy(() -> service.parseAccessToken(signup))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
	}

	@Test
	void 액세스_토큰은_가입_토큰으로_쓸_수_없다() {
		// given
		String access = service.issueAccessToken(42L).token();

		// when / then
		assertThatThrownBy(() -> service.parseSignupToken(access))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_SIGNUP_EXPIRED));
	}

	@Test
	void 가입_토큰은_10분이_지나면_AUTH_SIGNUP_EXPIRED다() {
		// given
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "g:with:colon");
		String token = service.issueSignupToken(identity);

		// when / then
		clock.advance(Duration.ofMinutes(9).plusSeconds(59));
		assertThat(service.parseSignupToken(token)).isEqualTo(identity);

		clock.advance(Duration.ofSeconds(2));
		assertThatThrownBy(() -> service.parseSignupToken(token))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_SIGNUP_EXPIRED));
	}

	@Test
	void 서명이_바뀐_토큰은_401이다() {
		// given
		String token = service.issueAccessToken(42L).token();
		// 서명 마지막 글자는 base64url 패딩 비트라 바꿔도 바이트가 같을 수 있으므로 서명 첫 글자를 바꾼다.
		int sigStart = token.lastIndexOf('.') + 1;
		char first = token.charAt(sigStart);
		String tampered = token.substring(0, sigStart) + (first == 'A' ? 'B' : 'A') + token.substring(sigStart + 1);

		// when / then
		assertThatThrownBy(() -> service.parseAccessToken(tampered))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
	}

	@Test
	void 비밀키가_32바이트보다_짧으면_생성에_실패한다() {
		// when / then
		assertThatThrownBy(() -> new JwtTokenService(props("short-secret"), clock))
				.isInstanceOf(IllegalStateException.class);
	}

}
