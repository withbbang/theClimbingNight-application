package com.theclimbingnight.auth.social;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
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
		return props(googleAud, appleAud, "12345");
	}

	static AuthProperties props(List<String> googleAud, List<String> appleAud, String kakaoAppId) {
		return new AuthProperties("secret", Duration.ofMinutes(30), Duration.ofDays(30), Duration.ofMinutes(10),
				new AuthProperties.Kakao("https://kapi.kakao.com", kakaoAppId),
				new AuthProperties.Provider("https://openapi.naver.com"),
				new AuthProperties.Google(googleAud, "https://example.invalid/google"),
				new AuthProperties.Apple(appleAud, "https://example.invalid/apple", APPLE_BASE_URL, APPLE_CLIENT_ID,
						"", "", ""));
	}

	static final String APPLE_BASE_URL = "https://appleid.apple.com";
	static final String APPLE_CLIENT_ID = "com.theclimbingnight.app";

	/** Apple 토큰 폐기 키까지 채운 설정. privateKeyPem이 비면 폐기가 꺼진다. */
	static AuthProperties appleRevokeProps(String teamId, String keyId, String privateKeyPem) {
		AuthProperties base = props(List.of(), List.of(APPLE_CLIENT_ID));
		return new AuthProperties(base.jwtSecret(), base.accessTtl(), base.refreshTtl(), base.signupTtl(),
				base.kakao(), base.naver(), base.google(),
				new AuthProperties.Apple(List.of(APPLE_CLIENT_ID), "https://example.invalid/apple", APPLE_BASE_URL,
						APPLE_CLIENT_ID, teamId, keyId, privateKeyPem));
	}

	/** 테스트용 P-256 키와 그 PKCS#8 PEM(.p8과 같은 형식). */
	static ECKey newEcKey() {
		try {
			return new ECKeyGenerator(Curve.P_256).generate();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static String pemOf(ECKey key) {
		try {
			String body = java.util.Base64.getMimeEncoder(64, "\n".getBytes())
					.encodeToString(key.toECPrivateKey().getEncoded());
			return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Apple 토큰 응답의 id_token 흉내. 서명은 검사하지 않으므로 아무 키로 서명한다. */
	static String idTokenWithSubject(String sub) {
		return sign(newKey("k"), "https://appleid.apple.com", APPLE_CLIENT_ID, sub,
				java.time.Instant.now().plusSeconds(600));
	}

}
