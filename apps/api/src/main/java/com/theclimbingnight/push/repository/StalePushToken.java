package com.theclimbingnight.push.repository;

import java.time.Instant;

/** 정리 대상 토큰과 조회 때의 마지막 등록 시각. 토큰 원문이 들어 있어 toString에서 뺀다. */
public record StalePushToken(String token, Instant lastSeenAt) {

	@Override
	public String toString() {
		return "StalePushToken[lastSeenAt=" + lastSeenAt + "]";
	}
}
