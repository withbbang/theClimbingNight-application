package com.theclimbingnight.moderation.dto;

import com.theclimbingnight.feed.dto.PostAuthorResponse;

/** 신고 목록의 댓글 대상. 운영진이 판단할 본문과 작성자, 원 게시물 id만 준다. */
public record ReportCommentResponse(long id, long postId, PostAuthorResponse author, String body) {
}
