package com.theclimbingnight.auth.social;

import com.nimbusds.jwt.SignedJWT;
import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.text.ParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Apple 로그인 사용자가 탈퇴할 때 Apple 쪽 토큰을 폐기한다 (App Store 심사 5.1.1(v)).
 * 앱이 탈퇴 직전 재인증으로 받은 authorization code를 refresh token으로 바꾼 뒤, 탈퇴가 커밋되면 폐기한다.
 * 토큰은 저장하지 않고, 원문을 로그에 남기지 않는다.
 */
@Component
public class AppleTokenRevoker {

	private static final Logger log = LoggerFactory.getLogger(AppleTokenRevoker.class);

	private final RestClient restClient;
	private final AuthProperties.Apple apple;
	private final AppleClientSecret clientSecret;

	public AppleTokenRevoker(@Qualifier("socialRestClient") RestClient restClient, AuthProperties properties,
			AppleClientSecret clientSecret) {
		this.restClient = restClient;
		this.apple = properties.apple();
		this.clientSecret = clientSecret;
	}

	public boolean enabled() {
		return apple.revokeConfigured();
	}

	/** code를 refresh token으로 바꾼다. code가 이 사용자(expectedSubject)의 것이 아니면 재인증을 요구한다. */
	public String exchange(String authorizationCode, String expectedSubject) {
		MultiValueMap<String, String> form = baseForm();
		form.add("code", authorizationCode);
		form.add("grant_type", "authorization_code");
		Map<?, ?> body;
		try {
			body = post("/auth/token", form).body(Map.class);
		} catch (RestClientResponseException e) {
			throw new BusinessException(e.getStatusCode().is4xxClientError()
					? ErrorCode.AUTH_APPLE_REAUTH_REQUIRED
					: ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		}
		Object refreshToken = body == null ? null : body.get("refresh_token");
		Object idToken = body == null ? null : body.get("id_token");
		if (!(refreshToken instanceof String token) || !(idToken instanceof String id)
				|| !expectedSubject.equals(subjectOf(id))) {
			throw new BusinessException(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
		}
		return token;
	}

	/** 실패해도 던지지 않는다. 탈퇴는 이미 끝났고, 사용자가 다시 할 수 있는 일이 없다. */
	public void revoke(String refreshToken) {
		MultiValueMap<String, String> form = baseForm();
		form.add("token", refreshToken);
		form.add("token_type_hint", "refresh_token");
		try {
			post("/auth/revoke", form).toBodilessEntity();
		} catch (RestClientResponseException e) {
			log.warn("Apple 토큰 폐기 실패: status={}", e.getStatusCode().value());
		} catch (RuntimeException e) {
			log.warn("Apple 토큰 폐기 실패: {}", e.getClass().getSimpleName());
		}
	}

	private MultiValueMap<String, String> baseForm() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", apple.clientId());
		form.add("client_secret", clientSecret.create());
		return form;
	}

	private RestClient.ResponseSpec post(String path, MultiValueMap<String, String> form) {
		return restClient.post()
				.uri(apple.baseUrl() + path)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve();
	}

	/** Apple과 직접 TLS로 받은 응답이라 서명은 다시 검사하지 않는다. */
	private static String subjectOf(String idToken) {
		try {
			return SignedJWT.parse(idToken).getJWTClaimsSet().getSubject();
		} catch (ParseException e) {
			return null;
		}
	}

}
