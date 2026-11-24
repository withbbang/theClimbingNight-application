package com.theclimbingnight.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** accessToken은 비밀값이다. toString에 넣지 않는다. */
@ConfigurationProperties("app.push")
public record PushProperties(boolean enabled, String baseUrl, String accessToken) {

	@Override
	public String toString() {
		return "PushProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + "]";
	}
}
