package com.theclimbingnight.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.PresignedUpload;
import com.theclimbingnight.profile.dto.ProfileImageStatusResponse;
import com.theclimbingnight.profile.dto.ProfileImageUploadResponse;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");
	private static final long ME = 7L;

	@Mock ProfileImageRepository images;
	@Mock UserRepository users;
	@Mock MediaStorage storage;
	@Mock MediaJobRepository jobs;
	@Mock PostMediaRepository postMedia;

	private ProfileImageService service;
	private User me;

	@BeforeEach
	void setUp() {
		MutableClock clock = new MutableClock(NOW);
		MediaJobScheduler scheduler = new MediaJobScheduler(jobs, postMedia, clock);
		service = new ProfileImageService(images, users, storage, scheduler, new SecureRandom());
		me = User.create("climber");
		ReflectionTestUtils.setField(me, "id", ME);
		org.mockito.Mockito.lenient().when(users.findByIdForUpdate(ME)).thenReturn(Optional.of(me));
	}

	private ProfileImage image(long id, long userId, ProfileImageStatus status, long byteSize) {
		ProfileImage image = ProfileImage.start(userId, "uploads/u" + id + ".jpg", "a/k" + id, byteSize);
		ReflectionTestUtils.setField(image, "id", id);
		ReflectionTestUtils.setField(image, "status", status);
		return image;
	}

	@SuppressWarnings("unchecked")
	private List<MediaJob> savedAll() {
		ArgumentCaptor<List<MediaJob>> captor = ArgumentCaptor.forClass(List.class);
		verify(jobs).saveAll(captor.capture());
		return captor.getValue();
	}

	@Test
	void startDropsOldUploading() {
		// given
		ProfileImage old = image(1L, ME, ProfileImageStatus.UPLOADING, 100L);
		given(images.findAllByUserIdAndStatus(ME, ProfileImageStatus.UPLOADING)).willReturn(List.of(old));
		given(images.countByUserIdAndStatus(ME, ProfileImageStatus.PROCESSING)).willReturn(0L);
		given(images.save(any(ProfileImage.class))).willAnswer(inv -> {
			ProfileImage saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 2L);
			return saved;
		});
		given(storage.presignUpload(anyString(), eq("image/jpeg"), eq(1000L), eq(Duration.ofMinutes(15))))
				.willReturn(new PresignedUpload("https://r2/put", Map.of("Content-Type", "image/jpeg"),
						NOW.plus(Duration.ofMinutes(15))));

		// when
		ProfileImageUploadResponse response = service.start(ME, 1000L);

		// then
		verify(images).deleteAll(List.of(old));
		ArgumentCaptor<ProfileImage> saved = ArgumentCaptor.forClass(ProfileImage.class);
		verify(images).save(saved.capture());
		assertThat(saved.getValue().getUploadKey()).matches("uploads/[0-9a-f]{32}\\.jpg");
		assertThat(saved.getValue().getPublicKey()).matches("a/[0-9a-f]{32}");
		assertThat(saved.getValue().getByteSize()).isEqualTo(1000L);
		assertThat(response.id()).isEqualTo(2L);
		assertThat(response.url()).isEqualTo("https://r2/put");
		assertThat(response.toString()).doesNotContain("r2/put");
	}

	@Test
	void startRejectsWhenTwoProcessing() {
		// given
		given(images.findAllByUserIdAndStatus(ME, ProfileImageStatus.UPLOADING)).willReturn(List.of());
		given(images.countByUserIdAndStatus(ME, ProfileImageStatus.PROCESSING)).willReturn(2L);

		// when / then
		assertThatThrownBy(() -> service.start(ME, 1000L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROFILE_IMAGE_BUSY));
		verify(images, never()).save(any());
	}

	@Test
	void confirmChecksUpload() {
		// given
		ProfileImage image = image(5L, ME, ProfileImageStatus.UPLOADING, 1000L);
		given(images.findByIdForUpdate(5L)).willReturn(Optional.of(image));
		given(storage.uploadedSize("uploads/u5.jpg")).willReturn(OptionalLong.empty(), OptionalLong.of(999L),
				OptionalLong.of(1000L));

		// when / then
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> service.confirm(ME, 5L)).isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROFILE_IMAGE_UPLOAD_INCOMPLETE));
		}
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.UPLOADING);
		verify(jobs, never()).save(any());

		ProfileImageStatusResponse response = service.confirm(ME, 5L);
		assertThat(response).isEqualTo(new ProfileImageStatusResponse(5L, "PROCESSING"));
		assertThat(image.getStatus()).isEqualTo(ProfileImageStatus.PROCESSING);
		ArgumentCaptor<MediaJob> job = ArgumentCaptor.forClass(MediaJob.class);
		verify(jobs).save(job.capture());
		assertThat(job.getValue().getKind()).isEqualTo(MediaJobKind.PROCESS);
		assertThat(job.getValue().getTargetType()).isEqualTo(MediaTargetType.PROFILE_IMAGE);
		assertThat(job.getValue().getTargetId()).isEqualTo(5L);
	}

	@Test
	void confirmOthersImageIs404() {
		given(images.findByIdForUpdate(5L)).willReturn(Optional.of(image(5L, 99L, ProfileImageStatus.UPLOADING, 1L)));

		assertThatThrownBy(() -> service.confirm(ME, 5L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void confirmTwiceIsNoop() {
		ProfileImage image = image(5L, ME, ProfileImageStatus.PROCESSING, 1000L);
		given(images.findByIdForUpdate(5L)).willReturn(Optional.of(image));

		ProfileImageStatusResponse response = service.confirm(ME, 5L);

		assertThat(response.status()).isEqualTo("PROCESSING");
		verify(jobs, never()).save(any());
		verify(storage, never()).uploadedSize(anyString());
	}

	@Test
	void statusOnlyOwner() {
		given(images.findById(5L)).willReturn(Optional.of(image(5L, 99L, ProfileImageStatus.READY, 1L)));
		given(images.findById(6L)).willReturn(Optional.of(image(6L, ME, ProfileImageStatus.READY, 1L)));

		assertThatThrownBy(() -> service.status(ME, 5L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThat(service.status(ME, 6L)).isEqualTo(new ProfileImageStatusResponse(6L, "READY"));
	}

	@Test
	void resetDropsCurrent() {
		// given
		ProfileImage current = image(5L, ME, ProfileImageStatus.READY, 1L);
		me.useProfileImage(5L);
		given(images.findAllByUserIdAndStatus(ME, ProfileImageStatus.PROCESSING)).willReturn(List.of());
		given(images.findById(5L)).willReturn(Optional.of(current));

		// when
		service.reset(ME);

		// then
		assertThat(me.getProfileImageId()).isNull();
		assertThat(current.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		assertThat(savedAll()).extracting(MediaJob::getKind, MediaJob::getTargetType, MediaJob::getTargetId,
				MediaJob::getNextRunAt).containsExactly(
						org.assertj.core.groups.Tuple.tuple(MediaJobKind.DELETE_MEDIA, MediaTargetType.PROFILE_IMAGE, 5L, NOW),
						org.assertj.core.groups.Tuple.tuple(MediaJobKind.PURGE_ORIGINAL, MediaTargetType.PROFILE_IMAGE, 5L,
								NOW.plus(Duration.ofDays(30))));
	}

	@Test
	void resetWithoutCurrentDoesNothing() {
		given(images.findAllByUserIdAndStatus(ME, ProfileImageStatus.PROCESSING)).willReturn(List.of());

		service.reset(ME);

		assertThat(me.getProfileImageId()).isNull();
		verify(jobs, never()).saveAll(any());
	}

	@Test
	void resetAlsoDropsProcessing() {
		// given
		ProfileImage processing = image(6L, ME, ProfileImageStatus.PROCESSING, 1L);
		given(images.findAllByUserIdAndStatus(ME, ProfileImageStatus.PROCESSING)).willReturn(List.of(processing));

		// when
		service.reset(ME);

		// then
		assertThat(processing.getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		verify(jobs).cancelQueued(List.of(6L), MediaTargetType.PROFILE_IMAGE, MediaJobKind.PROCESS);
		assertThat(new ArrayList<>(savedAll())).extracting(MediaJob::getKind)
				.containsExactly(MediaJobKind.DELETE_MEDIA, MediaJobKind.PURGE_ORIGINAL);
	}
}
