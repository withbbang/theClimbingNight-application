package com.theclimbingnight.auth.social;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.theclimbingnight.auth.AuthProperties;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SocialConfig {

	private static final int TIMEOUT_MILLIS = 5000;

	@Bean
	RestClient socialRestClient() {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withTimeouts(Duration.ofMillis(TIMEOUT_MILLIS), Duration.ofMillis(TIMEOUT_MILLIS));
		return RestClient.builder()
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}

	@Bean
	JWKSource<SecurityContext> googleJwkSource(AuthProperties properties) {
		return remoteJwkSource(properties.google().jwksUrl());
	}

	@Bean
	JWKSource<SecurityContext> appleJwkSource(AuthProperties properties) {
		return remoteJwkSource(properties.apple().jwksUrl());
	}

	/** 캐시·재시도·속도 제한이 붙은 원격 JWKS 소스. */
	private static JWKSource<SecurityContext> remoteJwkSource(String jwksUrl) {
		try {
			DefaultResourceRetriever retriever = new DefaultResourceRetriever(TIMEOUT_MILLIS, TIMEOUT_MILLIS,
					JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT);
			return JWKSourceBuilder.<SecurityContext>create(URI.create(jwksUrl).toURL(), retriever)
					.retrying(true)
					.build();
		} catch (MalformedURLException e) {
			throw new IllegalStateException("JWKS URL이 올바르지 않습니다: " + jwksUrl, e);
		}
	}

}
