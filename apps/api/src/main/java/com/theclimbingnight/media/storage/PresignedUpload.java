package com.theclimbingnight.media.storage;

import java.time.Instant;
import java.util.Map;

/** 앱이 url로 PUT할 때 headers를 그대로 보내야 서명이 맞는다. url 전체는 비밀값이라 로그에 남기지 않는다. */
public record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {

	@Override
	public String toString() {
		return "PresignedUpload[expiresAt=" + expiresAt + "]";
	}
}
