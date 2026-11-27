package com.theclimbingnight.comment.service;

import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 트랜잭션 안에서 동기 실행된다. 좋아요를 지우고 댓글·답글을 소프트 삭제한 뒤 게시물의 두 수를 다시 센다.
 * 게시물 행을 id 오름차순으로 잠가(지운 글도) 다른 요청과 잠금 순서가 어긋나지 않게 한다.
 * 다른 사람의 답글은 지우지 않아, 부모는 삭제 자리로 남는다.
 */
@Component
public class ReactionWithdrawalListener {

	private final PostRepository posts;
	private final PostLikeRepository likes;
	private final CommentRepository comments;
	private final CommentCounter counter;
	private final Clock clock;

	public ReactionWithdrawalListener(PostRepository posts, PostLikeRepository likes, CommentRepository comments,
			CommentCounter counter, Clock clock) {
		this.posts = posts;
		this.likes = likes;
		this.comments = comments;
		this.counter = counter;
		this.clock = clock;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE + 6)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		long userId = event.userId();
		SortedSet<Long> postIds = new TreeSet<>(likes.findPostIdsByUserId(userId));
		postIds.addAll(comments.findLivePostIdsByAuthorId(userId));
		Instant now = clock.instant();
		for (long postId : postIds) {
			Post post = posts.findByIdForUpdate(postId).orElse(null);
			if (post == null) {
				continue;
			}
			likes.deleteLike(postId, userId);
			comments.softDeleteByPostIdAndAuthorId(postId, userId, now);
			if (post.getDeletedAt() == null) {
				post.updateLikeCount((int) likes.countByPostId(postId));
				counter.recount(post);
			}
		}
	}
}
