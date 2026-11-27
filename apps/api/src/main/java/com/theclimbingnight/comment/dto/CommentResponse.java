package com.theclimbingnight.comment.dto;

import com.theclimbingnight.feed.dto.PostAuthorResponse;
import java.time.OffsetDateTime;

/** 자리(DELETED·HIDDEN)는 author와 body가 null이다. replyToNickname은 답글에만 있고, 대상이 탈퇴했으면 null이다. */
public record CommentResponse(long id, Long parentId, PostAuthorResponse author, String body, String replyToNickname,
		OffsetDateTime createdAt, CommentDisplay display, int replyCount, boolean mine, boolean deletable) {
}
