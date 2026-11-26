package com.theclimbingnight.media.worker;

import static com.theclimbingnight.media.worker.WorkerFixtures.NOW;
import static com.theclimbingnight.media.worker.WorkerFixtures.POST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MediaJobProcessorTest {

	@Mock MediaJobRepository jobs;
	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;
	@Mock JobHandler processHandler;
	@Mock JobHandler quarantineHandler;

	private MediaJobProcessor processor;

	@BeforeEach
	void setUp() {
		lenient().when(processHandler.kind()).thenReturn(MediaJobKind.PROCESS);
		lenient().when(quarantineHandler.kind()).thenReturn(MediaJobKind.QUARANTINE);
		processor = new MediaJobProcessor(jobs, List.of(processHandler, quarantineHandler), new RetryPolicy(), posts,
				mediaRepository, WorkerFixtures.tx(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private MediaJob givenClaimed(MediaJobKind kind, int attempts) {
		MediaJob job = WorkerFixtures.job(kind, 11L, attempts);
		given(jobs.findClaimable(NOW, PageRequest.of(0, 1))).willReturn(List.of(job.getId()));
		given(jobs.claim(job.getId(), NOW)).willReturn(1);
		given(jobs.findById(job.getId())).willReturn(Optional.of(job));
		return job;
	}

	@Test
	void 할_작업이_없으면_false다() {
		// given
		given(jobs.findClaimable(NOW, PageRequest.of(0, 1))).willReturn(List.of());

		// when / then
		assertThat(processor.runNext()).isFalse();
	}

	@Test
	void 성공하면_DONE이다() throws Exception {
		// given
		MediaJob job = givenClaimed(MediaJobKind.QUARANTINE, 0);

		// when
		boolean worked = processor.runNext();

		// then
		assertThat(worked).isTrue();
		verify(quarantineHandler).handle(job);
		verify(processHandler, never()).handle(any());
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.DONE);
	}

	@Test
	void 남이_먼저_가져간_작업은_처리하지_않는다() throws Exception {
		// given
		given(jobs.findClaimable(NOW, PageRequest.of(0, 1))).willReturn(List.of(900L));
		given(jobs.claim(900L, NOW)).willReturn(0);

		// when
		boolean worked = processor.runNext();

		// then
		assertThat(worked).isTrue();
		verify(processHandler, never()).handle(any());
		verify(quarantineHandler, never()).handle(any());
	}

	@Test
	void 예외가_나면_attempts가_늘고_다음_시각에_다시_QUEUED다() throws Exception {
		// given
		MediaJob job = givenClaimed(MediaJobKind.PROCESS, 0);
		willThrow(new IllegalStateException("ffmpeg 종료 코드 1")).given(processHandler).handle(job);

		// when
		processor.runNext();

		// then
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.QUEUED);
		assertThat(job.getAttempts()).isEqualTo(1);
		assertThat(job.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
		assertThat(job.getLastError()).contains("ffmpeg 종료 코드 1");
	}

	@Test
	void 세_번째_실패면_FAILED이고_사진과_게시물도_FAILED다() throws Exception {
		// given
		MediaJob job = givenClaimed(MediaJobKind.PROCESS, 2);
		willThrow(new IllegalStateException("변환 실패")).given(processHandler).handle(job);
		PostMedia media = WorkerFixtures.media(11L, PostMediaStatus.PROCESSING, "p/k11");
		Post post = WorkerFixtures.post(PostStatus.PENDING);
		given(mediaRepository.findById(11L)).willReturn(Optional.of(media));
		given(posts.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));

		// when
		processor.runNext();

		// then
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.FAILED);
		assertThat(job.getAttempts()).isEqualTo(3);
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.FAILED);
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
	}

	@Test
	void 세_번째_실패라도_숨긴_글의_상태는_그대로다() throws Exception {
		// given
		MediaJob job = givenClaimed(MediaJobKind.PROCESS, 2);
		willThrow(new IllegalStateException("변환 실패")).given(processHandler).handle(job);
		PostMedia media = WorkerFixtures.media(11L, PostMediaStatus.PROCESSING, "p/k11");
		Post post = WorkerFixtures.post(PostStatus.HIDDEN);
		given(mediaRepository.findById(11L)).willReturn(Optional.of(media));
		given(posts.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));

		// when
		processor.runNext();

		// then
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.FAILED);
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
	}

	@Test
	void 격리_작업이_세_번째_실패하면_FAILED로_남고_사진은_그대로다() throws Exception {
		// given
		MediaJob job = givenClaimed(MediaJobKind.QUARANTINE, 2);
		willThrow(new IllegalStateException("R2 503")).given(quarantineHandler).handle(job);

		// when
		processor.runNext();

		// then
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.FAILED);
		assertThat(job.getLastError()).contains("R2 503");
		verify(mediaRepository, never()).findById(anyLong());
	}

	@Test
	void 시도_횟수를_다_쓴_작업은_실행하지_않고_FAILED다() throws Exception {
		// given: 처리 중 워커가 죽어 RUNNING에서 세 번 되돌려졌다
		MediaJob job = givenClaimed(MediaJobKind.QUARANTINE, 3);

		// when
		processor.runNext();

		// then
		verify(quarantineHandler, never()).handle(any());
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.FAILED);
		assertThat(job.getAttempts()).isEqualTo(4);
	}
}
