package com.theclimbingnight.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

	private static final String TYPE_CLAIM = "typ";
	private static final String TYPE_ACCESS = "access";
	private static final String TYPE_SIGNUP = "signup";
	private static final int MIN_SECRET_BYTES = 32;

	private final AuthProperties properties;
	private final Clock clock;
	private final byte[] secret;

	public JwtTokenService(AuthProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		String jwtSecret = properties.jwtSecret();
		if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT 비밀키는 32바이트 이상이어야 합니다.");
		}
		this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
	}

	public IssuedAccessToken issueAccessToken(long userId) {
		Instant expiresAt = clock.instant().plus(properties.accessTtl()).truncatedTo(ChronoUnit.SECONDS);
		return new IssuedAccessToken(sign(String.valueOf(userId), TYPE_ACCESS, expiresAt), expiresAt);
	}

	public long parseAccessToken(String token) {
		try {
			return Long.parseLong(verify(token, TYPE_ACCESS));
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}
	}

	public String issueSignupToken(SocialIdentity identity) {
		Instant expiresAt = clock.instant().plus(properties.signupTtl()).truncatedTo(ChronoUnit.SECONDS);
		return sign(identity.provider().name() + ":" + identity.providerUserId(), TYPE_SIGNUP, expiresAt);
	}

	public SocialIdentity parseSignupToken(String token) {
		try {
			String subject = verify(token, TYPE_SIGNUP);
			int idx = subject.indexOf(':');
			return new SocialIdentity(SocialProvider.valueOf(subject.substring(0, idx)),
					subject.substring(idx + 1));
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.AUTH_SIGNUP_EXPIRED);
		}
	}

	/** exp는 호출자가 계산한 값을 그대로 쓴다. JWT는 초 단위라 호출자가 미리 초 단위로 자른다. */
	private String sign(String subject, String type, Instant expiresAt) {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(subject)
				.claim(TYPE_CLAIM, type)
				.issueTime(Date.from(clock.instant()))
				.expirationTime(Date.from(expiresAt))
				.build();
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(secret));
			return jwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException("JWT 서명에 실패했습니다.", e);
		}
	}

	/** 서명·타입·만료를 검증하고 subject를 돌려준다. 실패하면 RuntimeException. */
	private String verify(String token, String expectedType) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
				throw new IllegalArgumentException("invalid signature");
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			if (!expectedType.equals(claims.getStringClaim(TYPE_CLAIM))) {
				throw new IllegalArgumentException("invalid type");
			}
			Date exp = claims.getExpirationTime();
			// nimbus 기본 검증기는 시스템 시계를 쓰므로 주입한 Clock으로 직접 판정한다.
			if (exp == null || !clock.instant().isBefore(exp.toInstant())) {
				throw new IllegalArgumentException("expired");
			}
			String subject = claims.getSubject();
			if (subject == null) {
				throw new IllegalArgumentException("no subject");
			}
			return subject;
		} catch (ParseException | JOSEException e) {
			throw new IllegalArgumentException("invalid token", e);
		}
	}

}
