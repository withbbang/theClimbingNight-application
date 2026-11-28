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
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
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
	@Mock JobHandler profileProcessHandler;
	@Mock ProfileImageRepository profileImages;
	@Mock com.theclimbingnight.media.job.MediaJobScheduler scheduler;
	@Mock com.theclimbingnight.user.repository.UserRepository users;

	private MediaJobProcessor processor;

	@BeforeEach
	void setUp() {
		lenient().when(processHandler.kind()).thenReturn(MediaJobKind.PROCESS);
		lenient().when(processHandler.targetType()).thenReturn(MediaTargetType.POST_MEDIA);
		lenient().when(quarantineHandler.kind()).thenReturn(MediaJobKind.QUARANTINE);
		lenient().when(quarantineHandler.targetType()).thenReturn(MediaTargetType.POST_MEDIA);
		lenient().when(profileProcessHandler.kind()).thenReturn(MediaJobKind.PROCESS);
		lenient().when(profileProcessHandler.targetType()).thenReturn(MediaTargetType.PROFILE_IMAGE);
		processor = new MediaJobProcessor(jobs, List.of(processHandler, quarantineHandler, profileProcessHandler),
				new RetryPolicy(), posts, mediaRepository, profileImages, users, scheduler, WorkerFixtures.tx(),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private MediaJob givenClaimed(MediaJobKind kind, int attempts) {
		return givenClaimed(WorkerFixtures.job(kind, 11L, attempts));
	}

	private MediaJob givenClaimed(MediaJob job) {
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

	@Test
	void 대상_종류에_맞는_처리기로_보낸다() throws Exception {
		// given
		MediaJob job = givenClaimed(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 7L, 0));

		// when
		processor.runNext();

		// then
		verify(profileProcessHandler).handle(job);
		verify(processHandler, never()).handle(any());
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.DONE);
	}

	@Test
	void 처리기가_없는_종류_쌍이면_실패로_기록한다() throws Exception {
		// given: 프로필 사진에는 격리 처리기를 등록하지 않았다
		MediaJob job = givenClaimed(WorkerFixtures.profileJob(MediaJobKind.QUARANTINE, 7L, 0));

		// when
		processor.runNext();

		// then
		verify(quarantineHandler, never()).handle(any());
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.QUEUED);
		assertThat(job.getLastError()).contains("처리기 없음");
	}

	@Test
	void 같은_종류_쌍의_처리기를_두_번_등록하면_거부한다() {
		// when / then
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MediaJobProcessor(jobs,
				List.of(processHandler, processHandler), new RetryPolicy(), posts, mediaRepository, profileImages,
				users, scheduler, WorkerFixtures.tx(), Clock.fixed(NOW, ZoneOffset.UTC)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 프로필_처리가_최종_실패하면_프로필_사진만_FAILED이고_같은_id의_게시물_사진은_그대로다() throws Exception {
		// given: id 7인 게시물 사진과 프로필 사진이 함께 있다
		MediaJob job = givenClaimed(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 7L, 2));
		willThrow(new IllegalStateException("변환 실패")).given(profileProcessHandler).handle(job);
		PostMedia media = WorkerFixtures.media(7L, PostMediaStatus.PROCESSING, "p/k7");
		lenient().when(mediaRepository.findById(7L)).thenReturn(Optional.of(media));
		ProfileImage image = WorkerFixtures.profileImage(7L, ProfileImageStatus.PROCESSING);
		given(profileImages.findUserIdById(7L)).willReturn(Optional.of(WorkerFixtures.USER_ID));
		given(profileImages.findByIdForUpdate(7L)).willReturn(Optional.of(image));

		// when
		processor.runNext();

		// then: 사용자 행을 먼저 잠근다
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(users, profileImages);
		order.verify(users).findByIdForUpdate(WorkerFixtures.USER_ID);
		order.verify(profileImages).findByIdForUpdate(7L);
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.FAILED);
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.FAILED);
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
		verify(mediaRepository, never()).findById(anyLong());
		// NAS 원본이 남지 않게 정리 작업을 만든다(잠금 순서 안에서)
		verify(scheduler).onProfileImageDropped(image);
	}

	@Test
	void 프로필_처리가_최종_실패해도_이미_내린_사진은_REMOVED_그대로다() throws Exception {
		// given: 처리 중에 사용자가 되돌렸다
		MediaJob job = givenClaimed(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 7L, 2));
		willThrow(new IllegalStateException("업로드 없음")).given(profileProcessHandler).handle(job);
		ProfileImage image = WorkerFixtures.profileImage(7L, ProfileImageStatus.REMOVED);
		given(profileImages.findUserIdById(7L)).willReturn(Optional.of(WorkerFixtures.USER_ID));
		given(profileImages.findByIdForUpdate(7L)).willReturn(Optional.of(image));

		// when
		processor.runNext();

		// then
		assertThat(job.getStatus()).isEqualTo(MediaJobStatus.FAILED);
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
	}
}
