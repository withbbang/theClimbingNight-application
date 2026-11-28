package com.theclimbingnight.profile.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** url 전체는 비밀값이다. 로그에 남기지 않는다. */
public record ProfileImageUploadResponse(long id, String url, Map<String, String> headers, OffsetDateTime expiresAt) {

	@Override
	public String toString() {
		return "ProfileImageUploadResponse[id=" + id + ", expiresAt=" + expiresAt + "]";
	}
}
