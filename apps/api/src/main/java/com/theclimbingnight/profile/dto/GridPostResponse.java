package com.theclimbingnight.profile.dto;

/** thumbnailUrl은 숨긴 글이면 null, status는 본인 요청에만 채운다. */
public record GridPostResponse(long id, String thumbnailUrl, int mediaCount, String status) {
}
