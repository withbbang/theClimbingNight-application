package com.theclimbingnight.media.job;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 게시물 상태 변화에 따라 미디어 작업을 만든다. 호출한 쪽의 트랜잭션에 참여한다. */
@Component
public class MediaJobScheduler {

	static final Duration ORIGINAL_RETENTION = Duration.ofDays(30);

	private final MediaJobRepository jobs;
	private final PostMediaRepository mediaRepository;
	private final Clock clock;

	public MediaJobScheduler(MediaJobRepository jobs, PostMediaRepository mediaRepository, Clock clock) {
		this.jobs = jobs;
		this.mediaRepository = mediaRepository;
		this.clock = clock;
	}

	public void enqueueProcess(List<PostMedia> media) {
		Instant now = clock.instant();
		jobs.saveAll(media.stream().map(m -> MediaJob.queue(MediaJobKind.PROCESS, m.getId(), now)).toList());
	}

	/** 삭제: 공개 사본은 바로 지우고 NAS 원본은 30일 뒤 지운다. */
	public void onPostRemoved(Post post) {
		List<PostMedia> media = mediaOf(post);
		if (media.isEmpty()) {
			return;
		}
		cancelProcess(media);
		Instant now = clock.instant();
		List<MediaJob> created = new ArrayList<>();
		for (PostMedia m : media) {
			created.add(MediaJob.queue(MediaJobKind.DELETE_MEDIA, m.getId(), now));
			created.add(MediaJob.queue(MediaJobKind.PURGE_ORIGINAL, m.getId(), now.plus(ORIGINAL_RETENTION)));
		}
		jobs.saveAll(created);
	}

	public void onPostHidden(Post post) {
		List<PostMedia> media = mediaOf(post);
		if (media.isEmpty()) {
			return;
		}
		cancelProcess(media);
		queueEach(media, MediaJobKind.QUARANTINE);
	}

	/**
	 * 숨김·격리는 사진을 READY로 둔다. 복원하면 사진을 PROCESSING으로 돌려, RELEASE가 사진을 하나씩 새 키로 옮길 때마다
	 * READY가 되고 마지막 사진까지 옮겨진 뒤에만 글이 보이게 한다(그 전엔 응답에 죽은 예전 URL도 나가지 않는다).
	 */
	public void onPostRestored(Post post) {
		List<PostMedia> media = mediaOf(post);
		media.stream().filter(m -> m.getStatus() == PostMediaStatus.READY).forEach(PostMedia::markProcessing);
		queueEach(media, MediaJobKind.RELEASE);
	}

	private void queueEach(List<PostMedia> media, MediaJobKind kind) {
		Instant now = clock.instant();
		jobs.saveAll(media.stream().map(m -> MediaJob.queue(kind, m.getId(), now)).toList());
	}

	private void cancelProcess(List<PostMedia> media) {
		jobs.cancelQueued(media.stream().map(PostMedia::getId).toList(), MediaJobKind.PROCESS);
	}

	private List<PostMedia> mediaOf(Post post) {
		return mediaRepository.findAllByPostIdOrderBySortOrder(post.getId());
	}
}
