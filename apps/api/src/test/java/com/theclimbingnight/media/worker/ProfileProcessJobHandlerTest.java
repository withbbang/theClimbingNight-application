package com.theclimbingnight.media.worker;

import static com.theclimbingnight.media.worker.WorkerFixtures.NOW;
import static com.theclimbingnight.media.worker.WorkerFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.profile.service.ProfileImageService;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileProcessJobHandlerTest {

	private static final String ORIGINAL = "profile/2026/11/11.jpg";

	@Mock MediaStorage storage;
	@Mock OriginalStore originals;
	@Mock ImageTranscoder transcoder;
	@Mock UserRepository users;
	@Mock ProfileImageRepository images;
	@Mock MediaJobRepository jobs;
	@Mock PostMediaRepository mediaRepository;
	@Mock MediaJobScheduler resetScheduler;

	private ProfileProcessJobHandler handler;
	private User user;
	private ProfileImage image;

	@BeforeEach
	void setUp() throws Exception {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		MediaJobScheduler scheduler = new MediaJobScheduler(jobs, mediaRepository, clock);
		handler = new ProfileProcessJobHandler(storage, originals, transcoder, users, images, scheduler,
				WorkerFixtures.tx(), clock);
		user = WorkerFixtures.user();
		image = WorkerFixtures.profileImage(11L, ProfileImageStatus.PROCESSING);
		lenient().when(images.findById(11L)).thenReturn(Optional.of(image));
		lenient().when(images.findByIdForUpdate(11L)).thenReturn(Optional.of(image));
		lenient().when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
		lenient().when(storage.openUpload("uploads/u11.jpg")).thenReturn(new ByteArrayInputStream(new byte[10]));
		lenient().when(originals.saveProfile(eq(11L), any(), eq(NOW))).thenReturn(ORIGINAL);
		lenient().when(transcoder.transcodeSquare(any(), any()))
				.thenAnswer(inv -> WorkerFixtures.square(inv.getArgument(1, Path.class)));
	}

	/** 두 번째 공개본을 올릴 때 경쟁하는 동작을 끼워 넣는다(커밋 직전). */
	private void onSecondPut(Runnable competing) {
		lenient().doAnswer(inv -> {
			if (inv.getArgument(0).equals("a/k11/s.webp")) {
				competing.run();
			}
			return null;
		}).when(storage).putPublic(anyString(), any(), anyString());
	}

	@SuppressWarnings("unchecked")
	private List<MediaJob> savedJobs() {
		ArgumentCaptor<List<MediaJob>> captor = ArgumentCaptor.forClass(List.class);
		verify(jobs, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
		List<MediaJob> all = new ArrayList<>();
		captor.getAllValues().forEach(all::addAll);
		return all;
	}

	private static void assertDropJobs(List<MediaJob> saved, long imageId) {
		assertThat(saved).allSatisfy(job -> {
			assertThat(job.getTargetType()).isEqualTo(MediaTargetType.PROFILE_IMAGE);
			assertThat(job.getTargetId()).isEqualTo(imageId);
		});
		assertThat(saved).extracting(MediaJob::getKind)
				.containsExactly(MediaJobKind.DELETE_MEDIA, MediaJobKind.PURGE_ORIGINAL);
	}

	@Test
	void 처리가_끝나면_지금_사진을_바꾸고_예전_사진은_정리_작업에_넘긴다() throws Exception {
		// given: 지금 사진은 A(10), 새 사진 B(11)
		ProfileImage current = WorkerFixtures.profileImage(10L, ProfileImageStatus.READY);
		user.useProfileImage(10L);
		given(images.findByIdForUpdate(10L)).willReturn(Optional.of(current));

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then
		verify(storage).putPublic(eq("a/k11/l.webp"), any(), eq("image/webp"));
		verify(storage).putPublic(eq("a/k11/s.webp"), any(), eq("image/webp"));
		verify(storage).deleteUpload("uploads/u11.jpg");
		verify(storage, never()).deletePublic(anyString());
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.READY);
		assertThat(image.getOriginalPath()).isEqualTo(ORIGINAL);
		assertThat(user.getProfileImageId()).isEqualTo(11L);
		assertThat(current.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		assertDropJobs(savedJobs(), 10L);
	}

	@Test
	void 사용자_행을_먼저_잠근_뒤_사진_행을_잠근다() throws Exception {
		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then: 원본 기록과 확정 모두 같은 순서
		InOrder order = inOrder(users, images);
		order.verify(users).findByIdForUpdate(USER_ID);
		order.verify(images).findByIdForUpdate(11L);
		order.verify(users).findByIdForUpdate(USER_ID);
		order.verify(images).findByIdForUpdate(11L);
	}

	@Test
	void 지금_사진이_없으면_새_사진만_지금_사진이_된다() throws Exception {
		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.READY);
		assertThat(user.getProfileImageId()).isEqualTo(11L);
		verify(jobs, never()).saveAll(anyList());
	}

	@Test
	void 늦게_끝난_예전_사진은_더_새_사진을_덮지_않는다() throws Exception {
		// given: B(11)·C(12) 둘 다 처리 중이고 B가 먼저 끝났다
		given(images.existsByUserIdAndStatusAndIdGreaterThan(USER_ID, ProfileImageStatus.PROCESSING, 11L))
				.willReturn(true);
		user.useProfileImage(5L);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then: B는 내리고 방금 올린 공개본은 정리 작업이 지운다
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		assertThat(user.getProfileImageId()).isEqualTo(5L);
		assertDropJobs(savedJobs(), 11L);
	}

	@Test
	void 더_새_사진이_이미_READY여도_덮지_않는다() throws Exception {
		// given
		lenient().when(images.existsByUserIdAndStatusAndIdGreaterThan(USER_ID, ProfileImageStatus.READY, 11L))
				.thenReturn(true);
		user.useProfileImage(12L);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		assertThat(user.getProfileImageId()).isEqualTo(12L);
		assertDropJobs(savedJobs(), 11L);
	}

	@Test
	void 커밋_직전에_되돌렸으면_사진이_없고_방금_올린_공개본을_지운다() throws Exception {
		// given: 업로드가 끝나고 커밋하기 직전에 사용자가 되돌린다
		ProfileImageService service = new ProfileImageService(images, users, storage, resetScheduler,
				new SecureRandom());
		given(images.findAllByUserIdAndStatus(USER_ID, ProfileImageStatus.PROCESSING)).willReturn(List.of(image));
		onSecondPut(() -> service.reset(USER_ID));

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then: 되돌리기가 만든 정리 작업 말고 워커는 작업을 만들지 않는다
		assertThat(user.getProfileImageId()).isNull();
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		verify(storage).deletePublic("a/k11/l.webp");
		verify(storage).deletePublic("a/k11/s.webp");
		verify(resetScheduler).onProfileImageDropped(image);
		verifyNoInteractions(jobs);
	}

	@Test
	void 커밋_직전에_탈퇴했으면_READY가_되지_않고_공개본은_정리_작업이_지운다() throws Exception {
		// given: 사용자 행만 탈퇴로 바뀌고 사진 행은 아직 PROCESSING
		onSecondPut(() -> user.withdraw(NOW));

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		assertThat(user.getProfileImageId()).isNull();
		assertDropJobs(savedJobs(), 11L);
		verify(storage, never()).deleteUpload(anyString());
	}

	@Test
	void 탈퇴가_사진까지_내렸으면_방금_올린_공개본을_바로_지운다() throws Exception {
		// given: 탈퇴 처리가 사진을 REMOVED로 바꾸고 정리 작업을 만들었다
		onSecondPut(() -> {
			user.withdraw(NOW);
			image.markRemoved();
		});

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		assertThat(user.getProfileImageId()).isNull();
		verify(storage).deletePublic("a/k11/l.webp");
		verify(storage).deletePublic("a/k11/s.webp");
		verifyNoInteractions(jobs);
	}

	@Test
	void 지난_시도가_READY까지_기록했으면_업로드만_지운다() throws Exception {
		// given
		org.springframework.test.util.ReflectionTestUtils.setField(image, "status", ProfileImageStatus.READY);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 1));

		// then
		verify(storage).deleteUpload("uploads/u11.jpg");
		verify(storage, never()).putPublic(anyString(), any(), anyString());
		verifyNoInteractions(originals, transcoder, users, jobs);
	}

	@Test
	void 처리_전에_내려진_사진은_받지도_않는다() throws Exception {
		// given
		image.markRemoved();

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0));

		// then
		verifyNoInteractions(storage, originals, transcoder, jobs);
	}

	@Test
	void 사진_행이_없으면_아무것도_안_한다() throws Exception {
		// given
		given(images.findById(99L)).willReturn(Optional.empty());

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 99L, 0));

		// then
		verifyNoInteractions(storage, originals, transcoder, jobs);
	}

	@Test
	void 공개본을_올리다_실패하면_올린_사본을_지우고_예외를_그대로_던진다() throws Exception {
		// given
		lenient().doAnswer(inv -> {
			if (inv.getArgument(0).equals("a/k11/s.webp")) {
				throw new IllegalStateException("R2 503");
			}
			return null;
		}).when(storage).putPublic(anyString(), any(), anyString());

		// when
		Throwable thrown = catchThrowable(() -> handler.handle(WorkerFixtures.profileJob(MediaJobKind.PROCESS, 11L, 0)));

		// then
		assertThat(thrown).hasMessage("R2 503");
		verify(storage).deletePublic("a/k11/l.webp");
		verify(storage).deletePublic("a/k11/s.webp");
		verify(storage, never()).deleteUpload(anyString());
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.PROCESSING);
		assertThat(user.getProfileImageId()).isNull();
	}
}
