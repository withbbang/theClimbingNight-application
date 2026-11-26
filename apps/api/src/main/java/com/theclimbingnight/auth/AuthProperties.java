package com.theclimbingnight.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		String jwtSecret,
		Duration accessTtl,
		Duration refreshTtl,
		Duration signupTtl,
		Kakao kakao,
		Provider naver,
		Google google,
		Apple apple) {

	public record Provider(String baseUrl) {
	}

	/** appId는 카카오 앱 ID. 토큰이 우리 앱에서 발급된 것인지 확인하는 데 쓴다. */
	public record Kakao(String baseUrl, String appId) {
	}

	public record Google(List<String> clientIds, String jwksUrl) {
	}

	/**
	 * clientId·teamId·keyId·privateKey는 탈퇴 때 Apple 토큰을 폐기하는 데 쓴다.
	 * privateKey는 Apple Developer에서 받은 .p8(PKCS#8 PEM) 본문. 셋 중 하나라도 비면 폐기를 하지 않는다.
	 */
	public record Apple(List<String> audiences, String jwksUrl, String baseUrl, String clientId, String teamId,
			String keyId, String privateKey) {

		public boolean revokeConfigured() {
			return hasText(teamId) && hasText(keyId) && hasText(privateKey);
		}

		private static boolean hasText(String value) {
			return value != null && !value.isBlank();
		}
	}

}
