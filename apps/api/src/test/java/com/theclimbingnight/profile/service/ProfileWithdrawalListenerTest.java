package com.theclimbingnight.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobStatus;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 탈퇴(UserService.withdraw)가 실제로 커밋된 뒤의 행을 본다. 테스트 트랜잭션 없이 돌려 리스너의 저장이
 * 영속성 컨텍스트에서 사라지지 않는지도 함께 확인한다. 쓴 행은 전후에 지운다.
 */
@SpringBootTest
class ProfileWithdrawalListenerTest {

	@Autowired UserService userService;
	@Autowired UserRepository users;
	@Autowired ProfileImageRepository images;
	@Autowired MediaJobRepository jobs;
	@Autowired Clock clock;

	private User leaving;
	private User staying;
	private final List<Long> userIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		leaving = users.save(User.create("프로필탈퇴A"));
		staying = users.save(User.create("프로필탈퇴B"));
		userIds.add(leaving.getId());
		userIds.add(staying.getId());
	}

	@AfterEach
	void tearDown() {
		for (long userId : userIds) {
			List<ProfileImage> owned = images.findAllByUserId(userId);
			List<Long> ids = owned.stream().map(ProfileImage::getId).toList();
			jobs.deleteAll(jobs.findAll().stream()
					.filter(j -> j.getTargetType() == MediaTargetType.PROFILE_IMAGE && ids.contains(j.getTargetId()))
					.toList());
			images.deleteAll(owned);
		}
		users.deleteAllById(userIds);
	}

	private ProfileImage image(long userId, String key, ProfileImageStatus status, String originalPath) {
		ProfileImage image = ProfileImage.start(userId, "uploads/" + key + ".jpg", "a/" + key, 100);
		switch (status) {
			case PROCESSING -> image.markProcessing();
			case READY -> image.markReady();
			case FAILED -> image.markFailed();
			case REMOVED -> image.markRemoved();
			default -> { }
		}
		if (originalPath != null) {
			image.recordOriginal(originalPath);
		}
		return images.save(image);
	}

	private List<MediaJob> jobsOf(long imageId) {
		return jobs.findAll().stream()
				.filter(j -> j.getTargetType() == MediaTargetType.PROFILE_IMAGE && j.getTargetId() == imageId)
				.toList();
	}

	private void assertDropped(ProfileImage image, Instant before) {
		assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		List<MediaJob> own = jobsOf(image.getId()).stream().filter(j -> j.getKind() != MediaJobKind.PROCESS).toList();
		assertThat(own).extracting(MediaJob::getKind)
				.containsExactlyInAnyOrder(MediaJobKind.DELETE_MEDIA, MediaJobKind.PURGE_ORIGINAL);
		assertThat(own).extracting(MediaJob::getStatus).containsOnly(MediaJobStatus.QUEUED);
		MediaJob purge = own.stream().filter(j -> j.getKind() == MediaJobKind.PURGE_ORIGINAL).findFirst().orElseThrow();
		assertThat(purge.getNextRunAt()).isAfter(before.plus(Duration.ofDays(29)));
	}

	@Test
	void withdrawDropsAllProfileImages() {
		// given: READY(지금 사진)·PROCESSING(큐에 처리 작업)·REMOVED(운영진이 내림)·UPLOADING
		ProfileImage ready = image(leaving.getId(), "w1", ProfileImageStatus.READY, "profile/2026/11/1.jpg");
		ProfileImage processing = image(leaving.getId(), "w2", ProfileImageStatus.PROCESSING, null);
		ProfileImage removed = image(leaving.getId(), "w3", ProfileImageStatus.REMOVED, "profile/2026/11/3.jpg");
		ProfileImage uploading = image(leaving.getId(), "w4", ProfileImageStatus.UPLOADING, null);
		ProfileImage others = image(staying.getId(), "w5", ProfileImageStatus.READY, null);
		MediaJob process = jobs.save(MediaJob.queue(MediaJobKind.PROCESS, MediaTargetType.PROFILE_IMAGE,
				processing.getId(), clock.instant()));
		User me = users.findById(leaving.getId()).orElseThrow();
		me.changeBio("안녕하세요");
		me.useProfileImage(ready.getId());
		users.save(me);
		Instant before = clock.instant();

		// when
		userService.withdraw(leaving.getId());

		// then: 커밋된 값을 새로 읽는다
		assertDropped(ready, before);
		assertDropped(processing, before);
		assertDropped(removed, before);
		assertThat(images.findById(uploading.getId())).isEmpty();
		assertThat(jobsOf(uploading.getId())).isEmpty();
		assertThat(jobs.findById(process.getId()).orElseThrow().getStatus()).isEqualTo(MediaJobStatus.CANCELED);
		User after = users.findById(leaving.getId()).orElseThrow();
		assertThat(after.getBio()).isNull();
		assertThat(after.getProfileImageId()).isNull();
		// 다른 사람 사진은 그대로
		assertThat(images.findById(others.getId()).orElseThrow().getStatus()).isEqualTo(ProfileImageStatus.READY);
		assertThat(jobsOf(others.getId())).isEmpty();
	}

	@Test
	void withdrawDropsFailedImageAndKeepsNothingOpen() {
		// given
		ProfileImage failed = image(leaving.getId(), "w6", ProfileImageStatus.FAILED, null);
		Instant before = clock.instant();

		// when
		userService.withdraw(leaving.getId());

		// then
		assertDropped(failed, before);
	}
}
