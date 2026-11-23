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
		Provider kakao,
		Provider naver,
		Google google,
		Apple apple) {

	public record Provider(String baseUrl) {
	}

	public record Google(List<String> clientIds, String jwksUrl) {
	}

	public record Apple(List<String> audiences, String jwksUrl) {
	}

}
