package com.theclimbingnight.profile.service;

import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 트랜잭션 안에서 동기 실행된다. 올리다 만 사진(UPLOADING)은 지우고, 나머지는 운영진이 내린 것까지 모두
 * REMOVED로 두고 공개 사본·격리본 삭제와 30일 뒤 원본 정리 작업을 만든다. 이미 REMOVED이고 원본 경로도 없는 사진은
 * 정리가 끝났으므로 건너뛴다. 사용자 행은 UserService.withdraw가 이미 잠갔고,
 * 사진 행은 하나씩 잠근 뒤 바꾼다. 사용자의 bio·profileImageId는 User.withdraw가 지운다.
 */
@Component
public class ProfileWithdrawalListener {

	private final ProfileImageRepository images;
	private final MediaJobScheduler scheduler;

	public ProfileWithdrawalListener(ProfileImageRepository images, MediaJobScheduler scheduler) {
		this.images = images;
		this.scheduler = scheduler;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE + 7)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		for (ProfileImage listed : images.findAllByUserId(event.userId())) {
			ProfileImage image = images.findByIdForUpdate(listed.getId()).orElse(null);
			if (image == null) {
				continue;
			}
			if (image.getStatus() == ProfileImageStatus.UPLOADING) {
				images.delete(image);
				continue;
			}
			if (image.getStatus() == ProfileImageStatus.REMOVED && image.getOriginalPath() == null) {
				// 원본까지 정리가 끝난 사진. 운영진이 내린 사진은 원본 경로가 남아 있어 여기서 정리한다.
				continue;
			}
			image.markRemoved();
			scheduler.onProfileImageDropped(image);
		}
	}
}
