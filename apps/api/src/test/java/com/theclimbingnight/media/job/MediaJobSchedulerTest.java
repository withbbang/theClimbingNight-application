package com.theclimbingnight.media.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MediaJobSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
	private static final long POST_ID = 50L;

	@Mock MediaJobRepository jobs;
	@Mock PostMediaRepository mediaRepository;

	private MediaJobScheduler scheduler;
	private Post post;

	@BeforeEach
	void setUp() {
		scheduler = new MediaJobScheduler(jobs, mediaRepository, new MutableClock(NOW));
		post = Post.draft(7L);
		ReflectionTestUtils.setField(post, "id", POST_ID);
	}

	private void givenMedia(long... ids) {
		List<PostMedia> media = new java.util.ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			PostMedia m = PostMedia.image(POST_ID, i, "uploads/u" + ids[i] + ".jpg", "p/k" + ids[i]);
			ReflectionTestUtils.setField(m, "id", ids[i]);
			media.add(m);
		}
		given(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).willReturn(media);
	}

	@SuppressWarnings("unchecked")
	private List<MediaJob> saved() {
		ArgumentCaptor<List<MediaJob>> captor = ArgumentCaptor.forClass(List.class);
		verify(jobs).saveAll(captor.capture());
		return captor.getValue();
	}

	@Test
	void 숨기면_대기_중인_변환을_취소하고_사진마다_격리_작업을_만든다() {
		// given
		givenMedia(11L, 12L);

		// when
		scheduler.onPostHidden(post);

		// then
		verify(jobs).cancelQueued(List.of(11L, 12L), MediaTargetType.POST_MEDIA, MediaJobKind.PROCESS);
		assertThat(saved()).extracting(MediaJob::getKind, MediaJob::getTargetId, MediaJob::getNextRunAt)
				.containsExactly(tuple(MediaJobKind.QUARANTINE, 11L, NOW), tuple(MediaJobKind.QUARANTINE, 12L, NOW));
	}

	@Test
	void 사진이_없는_글을_숨기면_작업을_만들지_않는다() {
		// given
		givenMedia();

		// when
		scheduler.onPostHidden(post);

		// then
		verifyNoInteractions(jobs);
	}

	@Test
	void 복원하면_사진마다_공개_작업을_만든다() {
		// given
		givenMedia(11L, 12L);

		// when
		scheduler.onPostRestored(post);

		// then
		assertThat(saved()).extracting(MediaJob::getKind, MediaJob::getTargetId)
				.containsExactly(tuple(MediaJobKind.RELEASE, 11L), tuple(MediaJobKind.RELEASE, 12L));
	}

	@Test
	void 복원하면_READY인_사진을_PROCESSING으로_돌려_모두_옮길_때까지_글이_보이지_않게_한다() {
		// given: 숨김·격리는 사진 상태를 READY로 둔다
		List<PostMedia> media = new java.util.ArrayList<>();
		for (long id : new long[] { 11L, 12L }) {
			PostMedia m = PostMedia.image(POST_ID, (int) id, "uploads/u" + id + ".jpg", "p/k" + id);
			ReflectionTestUtils.setField(m, "id", id);
			m.markReady("2026/11/" + id + ".jpg", 1080, 810, 1234L);
			media.add(m);
		}
		given(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).willReturn(media);

		// when
		scheduler.onPostRestored(post);

		// then
		assertThat(media).extracting(PostMedia::getStatus)
				.containsOnly(com.theclimbingnight.feed.entity.PostMediaStatus.PROCESSING);
	}

	@Test
	void 운영진이_프로필_사진을_내리면_대기_중인_변환을_취소하고_격리_작업만_만든다() {
		// given
		var image = com.theclimbingnight.profile.entity.ProfileImage.start(7L, "uploads/u.jpg", "a/k", 10L);
		ReflectionTestUtils.setField(image, "id", 31L);

		// when
		scheduler.onProfileImageTakenDown(image);

		// then
		verify(jobs).cancelQueued(List.of(31L), MediaTargetType.PROFILE_IMAGE, MediaJobKind.PROCESS);
		ArgumentCaptor<MediaJob> job = ArgumentCaptor.forClass(MediaJob.class);
		verify(jobs).save(job.capture());
		assertThat(job.getValue()).extracting(MediaJob::getKind, MediaJob::getTargetType, MediaJob::getTargetId)
				.containsExactly(MediaJobKind.QUARANTINE, MediaTargetType.PROFILE_IMAGE, 31L);
	}
}
