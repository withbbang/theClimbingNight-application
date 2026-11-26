package com.theclimbingnight.media;

import org.springframework.stereotype.Component;

/** DB에는 키만 두고, 응답에 내보낼 때 공개 주소를 붙인다. */
@Component
public class MediaUrls {

	private final String baseUrl;

	public MediaUrls(MediaProperties properties) {
		String base = properties.publicBaseUrl() == null ? "" : properties.publicBaseUrl();
		this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	public String publicUrl(String key) {
		return baseUrl + "/" + key;
	}
}
