package com.theclimbingnight.media.worker;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실행할 작업 하나를 가져와(조건부 UPDATE로 claim) (종류, 대상 종류)별 처리기에 넘긴다. 성공하면 DONE, 예외면 재시도
 * 정책에 따라 다시 QUEUED 또는 FAILED. 게시물 사진을 공개하는 작업(PROCESS·RELEASE)이 FAILED로 끝나면 사진도 FAILED로,
 * 게시물이 아직 PENDING이면 게시물도 FAILED로 둔다. 숨긴 글의 상태는 바꾸지 않는다. 프로필 사진 PROCESS가 FAILED로
 * 끝나면 사진이 아직 PROCESSING일 때만 FAILED로 둔다(그새 내려진 사진은 REMOVED 그대로).
 */
public class MediaJobProcessor {

	private static final Logger log = LoggerFactory.getLogger(MediaJobProcessor.class);
	private static final PageRequest ONE = PageRequest.of(0, 1);

	private final MediaJobRepository jobs;
	private final Map<HandlerKey, JobHandler> handlers = new HashMap<>();
	private final RetryPolicy retryPolicy;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final ProfileImageRepository profileImages;
	private final TransactionTemplate tx;
	private final Clock clock;

	public MediaJobProcessor(MediaJobRepository jobs, List<JobHandler> handlers, RetryPolicy retryPolicy,
			PostRepository posts, PostMediaRepository mediaRepository, ProfileImageRepository profileImages,
			TransactionTemplate tx, Clock clock) {
		this.jobs = jobs;
		for (JobHandler h : handlers) {
			HandlerKey key = new HandlerKey(h.kind(), h.targetType());
			if (this.handlers.putIfAbsent(key, h) != null) {
				throw new IllegalStateException("처리기 중복: " + key);
			}
		}
		this.retryPolicy = retryPolicy;
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.profileImages = profileImages;
		this.tx = tx;
		this.clock = clock;
	}

	/** 작업을 하나 가져가려 했으면 true(남이 먼저 가져갔어도), 할 작업이 없으면 false. */
	public boolean runNext() {
		Instant now = clock.instant();
		List<Long> ids = tx.execute(s -> jobs.findClaimable(now, ONE));
		if (ids == null || ids.isEmpty()) {
			return false;
		}
		long id = ids.get(0);
		Integer claimed = tx.execute(s -> jobs.claim(id, now));
		if (claimed == null || claimed == 0) {
			return true;
		}
		MediaJob job = tx.execute(s -> jobs.findById(id).orElse(null));
		if (job == null) {
			return true;
		}
		if (retryPolicy.exhausted(job.getAttempts())) {
			fail(job.getId(), "시도 횟수 초과(처리 중 중단 반복)");
			return true;
		}
		try {
			JobHandler handler = handlers.get(new HandlerKey(job.getKind(), job.getTargetType()));
			if (handler == null) {
				throw new IllegalStateException("처리기 없음: " + job.getKind() + "/" + job.getTargetType());
			}
			handler.handle(job);
		} catch (Exception e) {
			String error = e.getClass().getSimpleName() + ": " + e.getMessage();
			log.warn("미디어 작업 실패 id={} kind={} target={} attempts={} error={}", job.getId(), job.getKind(),
					job.getTargetId(), job.getAttempts() + 1, error);
			fail(job.getId(), error);
			return true;
		}
		tx.executeWithoutResult(s -> jobs.findById(id).ifPresent(MediaJob::markDone));
		return true;
	}

	private void fail(long jobId, String error) {
		tx.executeWithoutResult(s -> {
			MediaJob job = jobs.findById(jobId).orElse(null);
			if (job == null) {
				return;
			}
			Optional<Instant> next = retryPolicy.nextRunAt(job.getAttempts() + 1, clock.instant());
			if (next.isPresent()) {
				job.retryAt(next.get(), error);
				return;
			}
			job.markFailed(error);
			log.error("미디어 작업 최종 실패 id={} kind={} target={}", job.getId(), job.getKind(), job.getTargetId());
			if (job.getTargetType() == MediaTargetType.POST_MEDIA
					&& (job.getKind() == MediaJobKind.PROCESS || job.getKind() == MediaJobKind.RELEASE)) {
				failMedia(job.getTargetId());
			}
			if (job.getTargetType() == MediaTargetType.PROFILE_IMAGE && job.getKind() == MediaJobKind.PROCESS) {
				failProfileImage(job.getTargetId());
			}
		});
	}

	/** 사진 행을 잠가 읽어, 그새 되돌리기·탈퇴가 바꾼 REMOVED를 덮어쓰지 않는다. */
	private void failProfileImage(long imageId) {
		profileImages.findByIdForUpdate(imageId).filter(i -> i.getStatus() == ProfileImageStatus.PROCESSING)
				.ifPresent(ProfileImage::markFailed);
	}

	private void failMedia(long mediaId) {
		mediaRepository.findById(mediaId).ifPresent(media -> {
			media.markFailed();
			posts.findByIdForUpdate(media.getPostId()).filter(ProcessJobHandler::isPending).ifPresent(Post::markFailed);
		});
	}

	private record HandlerKey(MediaJobKind kind, MediaTargetType targetType) {
	}
}
