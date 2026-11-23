package com.theclimbingnight.auth.social;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.List;

/** Google·Apple 공통 ID 토큰(RS256) 검증. exp는 주입된 Clock 기준으로 직접 확인한다. */
public class IdTokenVerifier {

	private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
	private final List<String> allowedIssuers;
	private final List<String> allowedAudiences;
	private final Clock clock;

	public IdTokenVerifier(JWKSource<SecurityContext> jwkSource, List<String> allowedIssuers,
			List<String> allowedAudiences, Clock clock) {
		this.processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
		// 시각 검증은 Clock으로 직접 하므로 기본 검증기(시스템 시각)는 끈다.
		this.processor.setJWTClaimsSetVerifier((claims, context) -> {
		});
		this.allowedIssuers = List.copyOf(allowedIssuers);
		this.allowedAudiences = allowedAudiences == null ? List.of() : List.copyOf(allowedAudiences);
		this.clock = clock;
	}

	/** 검증을 통과하면 sub을 돌려준다. */
	public String verifyAndGetSubject(String token) {
		JWTClaimsSet claims;
		try {
			claims = processor.process(token, null);
		} catch (ParseException | BadJOSEException e) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
		} catch (JOSEException e) {
			// JWKS를 가져오지 못한 경우 등
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		}
		Date exp = claims.getExpirationTime();
		boolean valid = exp != null
				&& exp.toInstant().isAfter(clock.instant())
				&& claims.getIssuer() != null && allowedIssuers.contains(claims.getIssuer())
				&& claims.getAudience() != null && claims.getAudience().stream().anyMatch(allowedAudiences::contains)
				&& claims.getSubject() != null && !claims.getSubject().isBlank();
		if (!valid) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
		}
		return claims.getSubject();
	}

}
