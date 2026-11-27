package com.theclimbingnight.comment.repository;

/** 부모 댓글별로 보이는 답글(지워지지 않은 VISIBLE) 수. 보는 사람과 무관하다. */
public record ReplyCount(Long parentId, Long count) {
}
