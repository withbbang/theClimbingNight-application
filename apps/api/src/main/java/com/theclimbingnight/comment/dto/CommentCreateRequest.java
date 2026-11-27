package com.theclimbingnight.comment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CommentCreateRequest(@NotNull String body, Long replyToCommentId) {

	/** 앞뒤 공백을 자른 길이가 1~500자여야 한다. null은 @NotNull이 거른다. */
	@AssertTrue(message = "본문은 공백을 뺀 1~500자여야 합니다.")
	public boolean isBodyPresent() {
		if (body == null) {
			return true;
		}
		int length = body.strip().length();
		return length >= 1 && length <= 500;
	}
}
