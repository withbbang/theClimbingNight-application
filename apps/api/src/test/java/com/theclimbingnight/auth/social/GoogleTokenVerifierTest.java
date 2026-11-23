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

class GoogleTokenVerifierTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	private RSAKey key;
	private GoogleTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		key = SocialTestSupport.newKey("k1");
		verifier = new GoogleTokenVerifier(SocialTestSupport.sourceOf(key),
				SocialTestSupport.props(List.of("ios-client"), List.of()), new MutableClock(NOW));
	}

	@Test
	void 유효한_ID토큰이면_sub을_돌려준다() {
		// given
		String token = SocialTestSupport.sign(key, "https://accounts.google.com", "ios-client", "g-1",
				NOW.plus(Duration.ofMinutes(5)));

		// when
		SocialIdentity identity = verifier.verify(token);

		// then
		assertThat(identity).isEqualTo(new SocialIdentity(SocialProvider.GOOGLE, "g-1"));
	}

	@Test
	void 두_가지_iss_형식을_모두_허용한다() {
		// given
		String token = SocialTestSupport.sign(key, "accounts.google.com", "ios-client", "g-2",
				NOW.plus(Duration.ofMinutes(5)));

		// when
		SocialIdentity identity = verifier.verify(token);

		// then
		assertThat(identity.providerUserId()).isEqualTo("g-2");
	}

	@Test
	void aud가_다르면_401이다() {
		// given
		String token = SocialTestSupport.sign(key, "https://accounts.google.com", "other-client", "g-1",
				NOW.plus(Duration.ofMinutes(5)));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier.verify(token));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 만료된_토큰이면_401이다() {
		// given
		String token = SocialTestSupport.sign(key, "https://accounts.google.com", "ios-client", "g-1",
				NOW.minusSeconds(1));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier.verify(token));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 다른_키로_서명한_토큰이면_401이다() {
		// given
		RSAKey other = SocialTestSupport.newKey("k1");
		String token = SocialTestSupport.sign(other, "https://accounts.google.com", "ios-client", "g-1",
				NOW.plus(Duration.ofMinutes(5)));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier.verify(token));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

}
