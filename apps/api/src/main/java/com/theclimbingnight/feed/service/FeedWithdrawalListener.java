package com.theclimbingnight.feed.service;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 트랜잭션 안에서 동기 실행된다. 지워지지 않은 글을 모두 소프트 삭제하고 사진 삭제·원본 정리 작업을 만든다.
 * 신고와 차단 행은 남긴다. 글마다 행을 잠근 뒤 지워서, 동시에 숨김·복원이 와도 덮어쓰지 않는다.
 */
@Component
public class FeedWithdrawalListener {

	private final PostRepository posts;
	private final MediaJobScheduler scheduler;
	private final Clock clock;

	public FeedWithdrawalListener(PostRepository posts, MediaJobScheduler scheduler, Clock clock) {
		this.posts = posts;
		this.scheduler = scheduler;
		this.clock = clock;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE + 5)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		for (long postId : posts.findLiveIdsByAuthorId(event.userId())) {
			Post post = posts.findByIdForUpdate(postId).filter(p -> p.getDeletedAt() == null).orElse(null);
			if (post == null) {
				continue;
			}
			post.softDelete(clock.instant());
			scheduler.onPostRemoved(post);
		}
	}
}
