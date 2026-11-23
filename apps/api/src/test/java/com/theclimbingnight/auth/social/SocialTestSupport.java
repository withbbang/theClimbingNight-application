package com.theclimbingnight.auth.social;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.theclimbingnight.auth.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** 소셜 검증기 테스트용 RSA 키·토큰 생성 도구. */
final class SocialTestSupport {

	private SocialTestSupport() {
	}

	static RSAKey newKey(String kid) {
		try {
			return new RSAKeyGenerator(2048).keyID(kid).generate();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static JWKSource<SecurityContext> sourceOf(RSAKey key) {
		return new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK()));
	}

	static String sign(RSAKey key, String iss, String aud, String sub, Instant exp) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(iss).audience(aud).subject(sub)
					.expirationTime(Date.from(exp)).build();
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
					.keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static AuthProperties props(List<String> googleAud, List<String> appleAud) {
		return new AuthProperties("secret", Duration.ofMinutes(30), Duration.ofDays(30), Duration.ofMinutes(10),
				new AuthProperties.Provider("https://kapi.kakao.com"),
				new AuthProperties.Provider("https://openapi.naver.com"),
				new AuthProperties.Google(googleAud, "https://example.invalid/google"),
				new AuthProperties.Apple(appleAud, "https://example.invalid/apple"));
	}

}
