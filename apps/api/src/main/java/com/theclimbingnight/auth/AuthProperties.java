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

	public record Apple(List<String> audiences, String jwksUrl) {
	}

}
