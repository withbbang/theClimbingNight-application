package com.theclimbingnight.moderation.dto;

import java.time.OffsetDateTime;

/** 탈퇴한 사용자는 nickname이 null이다. 사진이 없으면 profileImageUrl이 null이다. */
public record BlockedUserResponse(long userId, String nickname, String profileImageUrl, OffsetDateTime blockedAt) {
}
