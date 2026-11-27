package com.theclimbingnight.feed.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import org.springframework.stereotype.Component;

/** 로그인한 사용자가 게시물을 볼 수 있는지와, 좋아요·댓글처럼 게시물 행을 잠그고 쓰는 규칙. 안 되면 모두 404다. */
@Component
public class PostAccess {

	private final PostRepository posts;
	private final UserBlockRepository blocks;

	public PostAccess(PostRepository posts, UserBlockRepository blocks) {
		this.posts = posts;
		this.blocks = blocks;
	}

	/**
	 * 지운 글은 404다. 작성자 본인에게는 DRAFT만 404고, 남에게는 VISIBLE이 아니거나 차단 관계면 404다.
	 */
	public Post readable(long viewerId, long postId) {
		Post post = posts.findById(postId).filter(p -> p.getDeletedAt() == null)
				.orElseThrow(PostAccess::notFound);
		checkReadable(post, viewerId);
		return post;
	}

	/** 행을 잠그고 쓸 수 있는 글(지워지지 않은 VISIBLE, 차단 관계 아님)만 준다. */
	public Post lockWritable(long viewerId, long postId) {
		Post post = lockLive(postId);
		if (post.getStatus() != PostStatus.VISIBLE || blocks.existsBetween(viewerId, post.getAuthorId())) {
			throw notFound();
		}
		return post;
	}

	/** 행을 잠근다. 지워지지 않았으면 상태와 상관없이 준다. */
	public Post lockLive(long postId) {
		return posts.findByIdForUpdate(postId).filter(p -> p.getDeletedAt() == null)
				.orElseThrow(PostAccess::notFound);
	}

	private void checkReadable(Post post, long viewerId) {
		if (post.getAuthorId().equals(viewerId)) {
			// 초안은 아직 글이 아니다. 그 밖의 상태는 작성자 본인에게 보인다.
			if (post.getStatus() == PostStatus.DRAFT) {
				throw notFound();
			}
		}
		else if (post.getStatus() != PostStatus.VISIBLE || blocks.existsBetween(viewerId, post.getAuthorId())) {
			throw notFound();
		}
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.COMMON_NOT_FOUND);
	}
}
