package com.theclimbingnight.media.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MediaJobRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

	@Autowired MediaJobRepository jobs;
	@Autowired PostRepository posts;
	@Autowired PostMediaRepository mediaRepository;
	@Autowired UserRepository users;

	private MediaJob queue(MediaJobKind kind, long target, Instant runAt) {
		return jobs.saveAndFlush(MediaJob.queue(kind, target, runAt));
	}

	private MediaJob reload(MediaJob job) {
		return jobs.findById(job.getId()).orElseThrow();
	}

	@Test
	void 실행할_작업은_우선순위_다음_id_순이고_미래_작업은_빠진다() {
		// given
		MediaJob process = queue(MediaJobKind.PROCESS, 1, NOW);
		MediaJob purge = queue(MediaJobKind.PURGE_ORIGINAL, 2, NOW);
		MediaJob quarantine = queue(MediaJobKind.QUARANTINE, 3, NOW);
		MediaJob process2 = queue(MediaJobKind.PROCESS, 4, NOW);
		queue(MediaJobKind.DELETE_MEDIA, 5, NOW.plusSeconds(1));

		// when
		List<Long> ids = jobs.findClaimable(NOW, PageRequest.of(0, 10));

		// then
		assertThat(ids).containsExactly(quarantine.getId(), process.getId(), process2.getId(), purge.getId());
	}

	@Test
	void 조건부_claim은_한_번만_성공한다() {
		// given
		MediaJob job = queue(MediaJobKind.PROCESS, 1, NOW);

		// when
		int first = jobs.claim(job.getId(), NOW);
		int second = jobs.claim(job.getId(), NOW);

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isEqualTo(0);
		assertThat(reload(job).getStatus()).isEqualTo(MediaJobStatus.RUNNING);
		assertThat(reload(job).getLockedAt()).isEqualTo(NOW);
	}

	@Test
	void 취소된_작업은_claim되지_않는다() {
		// given
		MediaJob job = queue(MediaJobKind.PROCESS, 7, NOW);

		// when
		int canceled = jobs.cancelQueued(List.of(7L, 8L), MediaJobKind.PROCESS);
		int claimed = jobs.claim(job.getId(), NOW);

		// then
		assertThat(canceled).isEqualTo(1);
		assertThat(claimed).isEqualTo(0);
		assertThat(reload(job).getStatus()).isEqualTo(MediaJobStatus.CANCELED);
	}

	@Test
	void 취소는_해당_종류의_QUEUED_작업만_바꾼다() {
		// given
		MediaJob queued = queue(MediaJobKind.PROCESS, 1, NOW);
		MediaJob running = queue(MediaJobKind.PROCESS, 1, NOW);
		jobs.claim(running.getId(), NOW);
		MediaJob otherKind = queue(MediaJobKind.RELEASE, 1, NOW);

		// when
		int canceled = jobs.cancelQueued(List.of(1L), MediaJobKind.PROCESS);

		// then
		assertThat(canceled).isEqualTo(1);
		assertThat(reload(queued).getStatus()).isEqualTo(MediaJobStatus.CANCELED);
		assertThat(reload(running).getStatus()).isEqualTo(MediaJobStatus.RUNNING);
		assertThat(reload(otherKind).getStatus()).isEqualTo(MediaJobStatus.QUEUED);
	}

	@Test
	void 십분_넘게_RUNNING인_작업만_다시_QUEUED가_된다() {
		// given
		Instant lockedBefore = NOW.minusSeconds(600);
		MediaJob stale = queue(MediaJobKind.PROCESS, 1, NOW);
		MediaJob boundary = queue(MediaJobKind.PROCESS, 2, NOW);
		MediaJob fresh = queue(MediaJobKind.PROCESS, 3, NOW);
		MediaJob queued = queue(MediaJobKind.PROCESS, 4, NOW);
		jobs.claim(stale.getId(), lockedBefore.minusSeconds(1));
		jobs.claim(boundary.getId(), lockedBefore);
		jobs.claim(fresh.getId(), NOW.minusSeconds(60));

		// when
		int requeued = jobs.requeueStale(lockedBefore);

		// then
		assertThat(requeued).isEqualTo(1);
		MediaJob reloaded = reload(stale);
		assertThat(reloaded.getStatus()).isEqualTo(MediaJobStatus.QUEUED);
		assertThat(reloaded.getAttempts()).isEqualTo(1);
		assertThat(reloaded.getLockedAt()).isNull();
		assertThat(reload(boundary).getStatus()).isEqualTo(MediaJobStatus.RUNNING);
		assertThat(reload(fresh).getStatus()).isEqualTo(MediaJobStatus.RUNNING);
		assertThat(reload(queued).getStatus()).isEqualTo(MediaJobStatus.QUEUED);
	}

	@Test
	void 오류_메시지는_500자로_자른다() {
		// given
		MediaJob job = queue(MediaJobKind.PROCESS, 1, NOW);

		// when
		job.markFailed("x".repeat(900));
		jobs.saveAndFlush(job);

		// then
		assertThat(reload(job).getLastError()).hasSize(500);
	}

	private long postWithMedia(long authorId, long[] mediaIds) {
		Post post = posts.saveAndFlush(
				Post.draft(authorId));
		for (int i = 0; i < mediaIds.length; i++) {
			mediaIds[i] = mediaRepository.saveAndFlush(PostMedia.image(post.getId(), i,
					"uploads/" + post.getId() + "-" + i + ".jpg", "p/" + post.getId() + "-" + i)).getId();
		}
		return post.getId();
	}

	@Test
	void 격리나_삭제_작업이_실패로_끝난_게시물만_고른다() {
		// given
		long author = users.saveAndFlush(User.create("작성자")).getId();
		long[] quarantined = new long[2];
		long[] deleted = new long[1];
		long[] processFailed = new long[1];
		long[] fine = new long[1];
		long quarantinedPost = postWithMedia(author, quarantined);
		long deletedPost = postWithMedia(author, deleted);
		long processFailedPost = postWithMedia(author, processFailed);
		long finePost = postWithMedia(author, fine);
		long otherPost = postWithMedia(author, new long[1]);
		MediaJob q = queue(MediaJobKind.QUARANTINE, quarantined[1], NOW);
		q.markFailed("R2 503");
		MediaJob d = queue(MediaJobKind.DELETE_MEDIA, deleted[0], NOW);
		d.markFailed("R2 503");
		MediaJob p = queue(MediaJobKind.PROCESS, processFailed[0], NOW);
		p.markFailed("ffmpeg");
		MediaJob ok = queue(MediaJobKind.QUARANTINE, fine[0], NOW);
		ok.markDone();
		jobs.saveAllAndFlush(List.of(q, d, p, ok));

		// when
		List<Long> failed = jobs.findPostIdsWithFailedCleanup(
				List.of(quarantinedPost, deletedPost, processFailedPost, finePost));

		// then
		assertThat(failed).containsExactlyInAnyOrder(quarantinedPost, deletedPost);
		assertThat(failed).doesNotContain(otherPost);
	}

	@Test
	void 정리가_실패한_뒤_나중_정리_작업이_성공하면_경고하지_않는다() {
		// given: 격리 실패 → 복원(RELEASE) 성공 → 다시 격리 성공한 사진, 격리 실패 → 복원만 성공한 사진
		long author = users.saveAndFlush(User.create("작성자")).getId();
		long[] requarantined = new long[1];
		long[] released = new long[1];
		long[] processedAfter = new long[1];
		long requarantinedPost = postWithMedia(author, requarantined);
		long releasedPost = postWithMedia(author, released);
		long processedAfterPost = postWithMedia(author, processedAfter);
		MediaJob q1 = queue(MediaJobKind.QUARANTINE, requarantined[0], NOW);
		q1.markFailed("R2 503");
		jobs.saveAndFlush(q1);
		MediaJob r1 = queue(MediaJobKind.RELEASE, requarantined[0], NOW);
		r1.markDone();
		jobs.saveAndFlush(r1);
		MediaJob q2 = queue(MediaJobKind.QUARANTINE, requarantined[0], NOW);
		q2.markDone();
		jobs.saveAndFlush(q2);
		MediaJob q3 = queue(MediaJobKind.QUARANTINE, released[0], NOW);
		q3.markFailed("R2 503");
		jobs.saveAndFlush(q3);
		MediaJob r2 = queue(MediaJobKind.RELEASE, released[0], NOW);
		r2.markDone();
		jobs.saveAndFlush(r2);
		// 정리와 무관한 작업은 실패를 덮지 않는다
		MediaJob q4 = queue(MediaJobKind.QUARANTINE, processedAfter[0], NOW);
		q4.markFailed("R2 503");
		jobs.saveAndFlush(q4);
		MediaJob p = queue(MediaJobKind.PROCESS, processedAfter[0], NOW);
		p.markDone();
		jobs.saveAndFlush(p);

		// when
		List<Long> failed = jobs.findPostIdsWithFailedCleanup(
				List.of(requarantinedPost, releasedPost, processedAfterPost));

		// then
		assertThat(failed).containsExactly(processedAfterPost);
	}
}
