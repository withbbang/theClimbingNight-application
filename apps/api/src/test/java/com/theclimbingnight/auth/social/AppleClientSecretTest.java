package com.theclimbingnight.auth.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.theclimbingnight.auth.service.MutableClock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppleClientSecretTest {

	private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

	@Test
	void 헤더와_클레임을_Apple_규격대로_서명한다() throws Exception {
		// given
		ECKey key = SocialTestSupport.newEcKey();
		AppleClientSecret secret = new AppleClientSecret(
				SocialTestSupport.appleRevokeProps("TEAM123456", "KEY1234567", SocialTestSupport.pemOf(key)),
				new MutableClock(NOW));

		// when
		SignedJWT jwt = SignedJWT.parse(secret.create());

		// then
		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
		assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY1234567");
		JWTClaimsSet claims = jwt.getJWTClaimsSet();
		assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
		assertThat(claims.getSubject()).isEqualTo("com.theclimbingnight.app");
		assertThat(claims.getAudience()).isEqualTo(List.of("https://appleid.apple.com"));
		assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
		assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(300));
		assertThat(jwt.verify(new ECDSAVerifier(key.toPublicJWK()))).isTrue();
	}

	@Test
	void 줄바꿈을_역슬래시n으로_적은_PEM도_읽는다() throws Exception {
		// given: 한 줄짜리 환경변수로 넣은 경우
		ECKey key = SocialTestSupport.newEcKey();
		String oneLine = SocialTestSupport.pemOf(key).replace("\n", "\\n");
		AppleClientSecret secret = new AppleClientSecret(
				SocialTestSupport.appleRevokeProps("TEAM123456", "KEY1234567", oneLine), new MutableClock(NOW));

		// when
		SignedJWT jwt = SignedJWT.parse(secret.create());

		// then
		assertThat(jwt.verify(new ECDSAVerifier(key.toPublicJWK()))).isTrue();
	}

}
