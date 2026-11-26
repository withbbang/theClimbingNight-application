package com.theclimbingnight.moderation.dto;

import java.time.OffsetDateTime;

/** 탈퇴한 사용자는 nickname이 null이다. */
public record BlockedUserResponse(long userId, String nickname, OffsetDateTime blockedAt) {
}
