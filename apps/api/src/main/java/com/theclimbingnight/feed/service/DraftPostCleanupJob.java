package com.theclimbingnight.feed.service;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시하지 않고 24시간이 지난 초안과 사진 행을 지운다. 업로드 객체는 버킷 수명 주기가 지운다.
 * 지우기 직전에 행을 잠그고 다시 DRAFT인지 봐서, 막 게시된 글을 지우지 않는다.
 */
@Component
public class DraftPostCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(DraftPostCleanupJob.class);
	static final Duration MAX_AGE = Duration.ofHours(24);

	private final PostRepository posts;
	private final PostMediaRepository media;
	private final Clock clock;

	public DraftPostCleanupJob(PostRepository posts, PostMediaRepository media, Clock clock) {
		this.posts = posts;
		this.media = media;
		this.clock = clock;
	}

	@Scheduled(cron = "0 15 * * * *", zone = "Asia/Seoul")
	@Transactional
	public void run() {
		Instant cutoff = clock.instant().minus(MAX_AGE);
		List<Long> ids = posts.findDraftIdsCreatedBefore(cutoff);
		int removed = 0;
		for (Long id : ids) {
			Post draft = posts.findByIdForUpdate(id).orElse(null);
			if (draft == null || draft.getStatus() != PostStatus.DRAFT) {
				continue;
			}
			media.deleteAll(media.findAllByPostIdOrderBySortOrder(id));
			posts.delete(draft);
			removed++;
		}
		if (removed > 0) {
			log.info("오래된 초안 {}개 삭제", removed);
		}
	}
}
