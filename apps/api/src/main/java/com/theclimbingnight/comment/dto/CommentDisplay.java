package com.theclimbingnight.comment.dto;

/** 목록에서 댓글을 어떻게 보일지. 자리(DELETED·HIDDEN)는 작성자와 본문을 내리지 않는다. */
public enum CommentDisplay {
	/** 보이는 댓글 */
	VISIBLE,
	/** 숨겨진 내 댓글. 본문과 안내를 보인다. */
	HIDDEN_MINE,
	/** 삭제 자리 */
	DELETED,
	/** 숨김 자리 */
	HIDDEN
}
