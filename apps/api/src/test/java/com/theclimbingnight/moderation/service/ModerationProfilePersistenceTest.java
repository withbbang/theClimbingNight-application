package com.theclimbingnight.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.service.CommentCounter;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.feed.service.PostResponseMapper;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.moderation.dto.ReportTargetActionRequest.Action;
import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
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
 * 운영진 프로필 조치가 실제 트랜잭션에서 저장되는지 본다. 작업 취소(벌크 UPDATE)가 영속성 컨텍스트를 비우면 그 뒤의
 * 엔티티 변경이 사라지는데 모의 저장소 테스트로는 보이지 않는다. 테스트 트랜잭션 없이 커밋하고 새로 읽는다.
 */
@DataJpaTest
@Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class, MediaJobScheduler.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ModerationProfilePersistenceTest {

	@Autowired UserRepository users;
	@Autowired ProfileImageRepository images;
	@Autowired MediaJobRepository jobs;
	@Autowired ReportRepository reports;
	@Autowired MediaJobScheduler scheduler;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired Clock clock;

	private TransactionTemplate tx;
	private ModerationService service;
	private final List<Long> userIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		service = new ModerationService(reports, mock(PostRepository.class), mock(CommentRepository.class),
				mock(CommentCounter.class), mock(PostMediaRepository.class), users, jobs, scheduler,
				mock(MediaStorage.class), mock(PostResponseMapper.class), mock(MediaUrls.class), mock(UserAvatars.class),
				images, clock);
	}

	@AfterEach
	void cleanUp() {
		tx.executeWithoutResult(s -> {
			for (long userId : userIds) {
				users.findById(userId).ifPresent(u -> u.useProfileImage(null));
			}
		});
		tx.executeWithoutResult(s -> reports.deleteAll(reports.findAll().stream()
				.filter(r -> userIds.contains(r.getReporterId()) || userIds.contains(r.getTargetId())).toList()));
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

	@Test
	void 프로필_사진을_내리면_저장소에_반영되고_격리_작업만_생긴다() {
		// given: 지금 사진 A와 열린 신고
		User reporter = user("신고자1");
		User target = user("대상1");
		ProfileImage a = tx.execute(s -> {
			ProfileImage image = ProfileImage.start(target.getId(), "uploads/ma1.jpg", "a/ma1", 100);
			image.markReady();
			return images.save(image);
		});
		tx.executeWithoutResult(s -> users.findById(target.getId()).orElseThrow().useProfileImage(a.getId()));
		Report report = tx.execute(s -> reports
				.save(Report.open(reporter.getId(), ReportTargetType.USER, target.getId(), ReportReason.SPAM, null)));

		// when
		tx.executeWithoutResult(s -> service.act(reporter.getId(), ReportTargetType.USER, target.getId(),
				Action.REMOVE_PROFILE_IMAGE));

		// then: 새로 읽은 값
		assertThat(users.findById(target.getId()).orElseThrow().getProfileImageId()).isNull();
		assertThat(images.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		List<MediaJob> jobsOfA = jobs.findAll().stream()
				.filter(j -> j.getTargetType() == MediaTargetType.PROFILE_IMAGE && j.getTargetId() == a.getId()).toList();
		assertThat(jobsOfA).extracting(MediaJob::getKind).containsExactly(MediaJobKind.QUARANTINE);
		Report saved = reports.findById(report.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(ReportStatus.ACTIONED);
		assertThat(saved.getHandledBy()).isEqualTo(reporter.getId());
	}

	@Test
	void 둘_다_내리면_한_트랜잭션에서_사진과_자기소개가_함께_저장된다() {
		// given: 지금 사진 B, 자기소개, 열린 신고
		User reporter = user("신고자2");
		User target = user("대상2");
		ProfileImage b = tx.execute(s -> {
			ProfileImage image = ProfileImage.start(target.getId(), "uploads/mb2.jpg", "a/mb2", 100);
			image.markReady();
			return images.save(image);
		});
		tx.executeWithoutResult(s -> {
			User u = users.findById(target.getId()).orElseThrow();
			u.useProfileImage(b.getId());
			u.changeBio("광고");
		});
		Report report = tx.execute(s -> reports
				.save(Report.open(reporter.getId(), ReportTargetType.USER, target.getId(), ReportReason.SPAM, null)));

		// when
		tx.executeWithoutResult(s -> service.act(reporter.getId(), ReportTargetType.USER, target.getId(),
				Action.REMOVE_PROFILE));

		// then: 새로 읽은 값. 작업 취소(벌크 UPDATE) 뒤의 자기소개 변경도 남아 있어야 한다.
		User after = users.findById(target.getId()).orElseThrow();
		assertThat(after.getProfileImageId()).isNull();
		assertThat(after.getBio()).isNull();
		assertThat(images.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(ProfileImageStatus.REMOVED);
		List<MediaJob> jobsOfB = jobs.findAll().stream()
				.filter(j -> j.getTargetType() == MediaTargetType.PROFILE_IMAGE && j.getTargetId() == b.getId()).toList();
		assertThat(jobsOfB).extracting(MediaJob::getKind).containsExactly(MediaJobKind.QUARANTINE);
		assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.ACTIONED);
	}
}
