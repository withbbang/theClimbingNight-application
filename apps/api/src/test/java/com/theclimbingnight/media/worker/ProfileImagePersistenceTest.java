package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.job.MediaJobStatus;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로필 사진 교체·되돌리기가 실제 트랜잭션에서 저장되는지 본다. 작업 취소(벌크 UPDATE)가 영속성 컨텍스트를 비우면
 * 그 뒤의 엔티티 변경이 사라지는데, 모의 저장소 테스트로는 보이지 않는다. 테스트 트랜잭션 없이 커밋하고 새로 읽는다.
 */
@DataJpaTest
@Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class, MediaJobScheduler.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfileImagePersistenceTest {

	@Autowired UserRepository users;
	@Autowired ProfileImageRepository images;
	@Autowired MediaJobRepository jobs;
	@Autowired MediaJobScheduler scheduler;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired Clock clock;

	private TransactionTemplate tx;
	private final List<Long> userIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
	}

	@AfterEach
	void cleanUp() {
		tx.executeWithoutResult(s -> {
			for (long userId : userIds) {
				users.findById(userId).ifPresent(u -> u.useProfileImage(null));
			}
		});
		tx.executeWithoutResult(s -> {
			for (long userId : userIds) {
				List<ProfileImage> owned = images.findAllByUserId(userId);
				List<Long> ids = owned.stream().map(ProfileImage::getId).toList();
				jobs.deleteAll(jobs.findAll().stream()
						.filter(j -> j.getTargetType() == MediaTargetType.PROFILE_IMAGE && ids.contains(j.getTargetId()))
						.toList());
				images.deleteAll(owned);
				users.deleteById(userId);
			}
		});
	}

	private User user(String nickname) {
		User user = tx.execute(s -> users.save(User.create(nickname)));
		userIds.add(user.getId());
		return user;
	}

	private ProfileImage image(long userId, String key, ProfileImageStatus status) {
		return tx.execute(s -> {
			ProfileImage image = ProfileImage.start(userId, "uploads/" + key + ".jpg", "a/" + key, 100);
			if (status == ProfileImageStatus.READY) {
				image.markReady();
			} else if (status == ProfileImageStatus.PROCESSING) {
				image.markProcessing();
			}
			return images.save(image);
		});
	}

	private void useImage(long userId, long imageId) {
		tx.executeWithoutResult(s -> users.findById(userId).orElseThrow().useProfileImage(imageId));
	}

	private MediaJob queueProcess(long imageId) {
		return tx.execute(s -> jobs.save(MediaJob.queue(MediaJobKind.PROCESS, MediaTargetType.PROFILE_IMAGE, imageId,
				clock.instant())));
	}

	private List<MediaJob> jobsOf(long imageId) {
		return jobs.findAll().stream()
				.filter(j -> j.getTargetType() == MediaTargetType.PROFILE_IMAGE && j.getTargetId() == imageId)
				.toList();
	}

	private ProfileImageStatus statusOf(long imageId) {
		return images.findById(imageId).orElseThrow().getStatus();
	}

	@Test
	void 처리가_끝나면_새_사진이_지금_사진으로_저장되고_예전_사진은_정리_작업과_함께_내려간다() throws Exception {
		// given: 지금 사진 A(READY), 새 사진 B(PROCESSING)
		User user = user("교체저장");
		ProfileImage a = image(user.getId(), "pa1", ProfileImageStatus.READY);
		useImage(user.getId(), a.getId());
		ProfileImage b = image(user.getId(), "pb1", ProfileImageStatus.PROCESSING);
		MediaJob job = queueProcess(b.getId());

		MediaStorage storage = mock(MediaStorage.class);
		OriginalStore originals = mock(OriginalStore.class);
		ImageTranscoder transcoder = mock(ImageTranscoder.class);
		given(storage.openUpload(anyString())).willReturn(new ByteArrayInputStream(new byte[10]));
		given(originals.saveProfile(anyLong(), any(), any())).willReturn("profile/2026/11/" + b.getId() + ".jpg");
		given(transcoder.transcodeSquare(any(), any()))
				.willAnswer(inv -> WorkerFixtures.square(inv.getArgument(1, Path.class)));
		ProfileProcessJobHandler handler = new ProfileProcessJobHandler(storage, originals, transcoder, users, images,
				scheduler, tx, clock);

		// when
		handler.handle(job);

		// then: 새로 읽은 값
		assertThat(users.findById(user.getId()).orElseThrow().getProfileImageId()).isEqualTo(b.getId());
		assertThat(statusOf(b.getId())).isEqualTo(ProfileImageStatus.READY);
		assertThat(statusOf(a.getId())).isEqualTo(ProfileImageStatus.REMOVED);
		assertThat(jobsOf(a.getId())).extracting(MediaJob::getKind)
				.containsExactlyInAnyOrder(MediaJobKind.DELETE_MEDIA, MediaJobKind.PURGE_ORIGINAL);
	}

	@Test
	void 되돌리면_지금_사진과_처리_중인_사진이_모두_내려가고_처리_작업이_취소된다() {
		// given: 지금 사진 A(READY), 처리 중 B·C
		User user = user("되돌리기저장");
		ProfileImage a = image(user.getId(), "pa2", ProfileImageStatus.READY);
		useImage(user.getId(), a.getId());
		ProfileImage b = image(user.getId(), "pb2", ProfileImageStatus.PROCESSING);
		ProfileImage c = image(user.getId(), "pc2", ProfileImageStatus.PROCESSING);
		MediaJob processB = queueProcess(b.getId());
		MediaJob processC = queueProcess(c.getId());
		ProfileImageService service = new ProfileImageService(images, users, mock(MediaStorage.class), scheduler,
				new SecureRandom());

		// when
		tx.executeWithoutResult(s -> service.reset(user.getId()));

		// then: 새로 읽은 값
		assertThat(users.findById(user.getId()).orElseThrow().getProfileImageId()).isNull();
		assertThat(List.of(statusOf(a.getId()), statusOf(b.getId()), statusOf(c.getId())))
				.containsOnly(ProfileImageStatus.REMOVED);
		assertThat(jobs.findById(processB.getId()).orElseThrow().getStatus()).isEqualTo(MediaJobStatus.CANCELED);
		assertThat(jobs.findById(processC.getId()).orElseThrow().getStatus()).isEqualTo(MediaJobStatus.CANCELED);
	}
}
