package com.theclimbingnight.profile.dto;

/** profileImage는 사진이 없으면 null. postCount는 보이는 글 수. */
public record ProfileResponse(long id, String nickname, String bio, ProfileImageUrls profileImage, long postCount,
		boolean mine) {
}
