package com.theclimbingnight.auth.social;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.theclimbingnight.auth.AuthProperties;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

/** Apple 토큰 API에 보내는 client_secret(ES256 JWT)을 만든다. */
@Component
public class AppleClientSecret {

	private static final String AUDIENCE = "https://appleid.apple.com";
	private static final Duration TTL = Duration.ofMinutes(5);

	private final AuthProperties.Apple apple;
	private final Clock clock;

	public AppleClientSecret(AuthProperties properties, Clock clock) {
		this.apple = properties.apple();
		this.clock = clock;
	}

	public String create() {
		Instant now = clock.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(apple.teamId())
				.subject(apple.clientId())
				.audience(AUDIENCE)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(TTL)))
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(apple.keyId()).build(), claims);
		try {
			jwt.sign(new ECDSASigner(privateKey()));
		} catch (JOSEException e) {
			throw new IllegalStateException("Apple client secret 서명에 실패했습니다.", e);
		}
		return jwt.serialize();
	}

	/** .p8(PKCS#8 PEM)을 읽는다. 환경변수에 한 줄로 넣느라 줄바꿈을 \n 글자로 적은 경우도 받는다. */
	private ECPrivateKey privateKey() {
		String base64 = apple.privateKey()
				.replace("\\n", "\n")
				.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			byte[] der = Base64.getDecoder().decode(base64);
			return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
			// 키 내용은 메시지에 싣지 않는다.
			throw new IllegalStateException("APPLE_PRIVATE_KEY를 읽지 못했습니다. .p8 파일 내용을 그대로 넣었는지 확인하세요.", e);
		}
	}

}
