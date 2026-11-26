package com.theclimbingnight.feed.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** url 전체는 비밀값이다. 로그에 남기지 않는다. */
public record PostUploadResponse(long mediaId, String url, Map<String, String> headers, OffsetDateTime expiresAt) {

	@Override
	public String toString() {
		return "PostUploadResponse[mediaId=" + mediaId + ", expiresAt=" + expiresAt + "]";
	}
}
