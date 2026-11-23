package com.theclimbingnight.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nimbusds.jose.jwk.RSAKey;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleTokenVerifierTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String APPLE_ISS = "https://appleid.apple.com";

	private RSAKey key;
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		key = SocialTestSupport.newKey("a1");
		clock = new MutableClock(NOW);
	}

	private AppleTokenVerifier verifier(List<String> audiences) {
		return new AppleTokenVerifier(SocialTestSupport.sourceOf(key),
				SocialTestSupport.props(List.of(), audiences), clock);
	}

	@Test
	void 유효한_ID토큰이면_sub을_돌려준다() {
		// given
		String token = SocialTestSupport.sign(key, APPLE_ISS, "com.example.app", "a-1", NOW.plus(Duration.ofMinutes(5)));

		// when
		SocialIdentity identity = verifier(List.of("com.example.app")).verify(token);

		// then
		assertThat(identity).isEqualTo(new SocialIdentity(SocialProvider.APPLE, "a-1"));
	}

	@Test
	void iss가_apple이_아니면_401이다() {
		// given
		String token = SocialTestSupport.sign(key, "https://evil.example.com", "com.example.app", "a-1",
				NOW.plus(Duration.ofMinutes(5)));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class,
				() -> verifier(List.of("com.example.app")).verify(token));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 허용_aud_목록이_비어_있으면_모든_토큰을_거부한다() {
		// given
		String token = SocialTestSupport.sign(key, APPLE_ISS, "com.example.app", "a-1", NOW.plus(Duration.ofMinutes(5)));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier(List.of()).verify(token));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

}
