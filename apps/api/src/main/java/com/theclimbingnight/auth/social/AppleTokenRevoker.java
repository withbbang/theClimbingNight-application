package com.theclimbingnight.auth.social;

import com.nimbusds.jwt.SignedJWT;
import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
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
	private static final Set<String> CONFIG_ERRORS = Set.of("invalid_client", "unauthorized_client");

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
			throw exchangeFailure(e);
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

	/**
	 * code 문제(invalid_grant 등)만 재인증으로 풀린다. 우리 키 설정이 틀린 invalid_client·unauthorized_client를
	 * 재인증으로 돌려주면 앱이 같은 오류를 반복하므로 장애로 본다. Apple의 error 값만 남기고 토큰·code는 남기지 않는다.
	 */
	private BusinessException exchangeFailure(RestClientResponseException e) {
		if (!e.getStatusCode().is4xxClientError()) {
			return new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		}
		String error = errorOf(e);
		log.warn("Apple 토큰 교환 거절: status={}, error={}", e.getStatusCode().value(), error);
		return CONFIG_ERRORS.contains(error)
				? new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE)
				: new BusinessException(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
	}

	private static String errorOf(RestClientResponseException e) {
		try {
			Map<?, ?> body = e.getResponseBodyAs(Map.class);
			Object error = body == null ? null : body.get("error");
			return error instanceof String value && value.matches("[a-z_]{1,40}") ? value : "unknown";
		} catch (RuntimeException parseFailure) {
			return "unknown";
		}
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
