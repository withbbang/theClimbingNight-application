package com.theclimbingnight.comment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(@NotNull @Size(max = 500) String body, Long replyToCommentId) {

	@AssertTrue(message = "본문을 입력해주세요.")
	public boolean isBodyPresent() {
		return body == null || !body.strip().isEmpty();
	}
}
