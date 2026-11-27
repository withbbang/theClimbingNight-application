package com.theclimbingnight.comment.service;

import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.feed.entity.Post;
import org.springframework.stereotype.Component;

/** 게시물의 comment_count를 실제 보이는 댓글 수로 맞춘다. 호출하는 쪽이 게시물 행을 잠근 상태여야 한다. */
@Component
public class CommentCounter {

	private final CommentRepository comments;

	public CommentCounter(CommentRepository comments) {
		this.comments = comments;
	}

	public void recount(Post lockedPost) {
		lockedPost.updateCommentCount(comments.countShownByPostId(lockedPost.getId()));
	}
}
