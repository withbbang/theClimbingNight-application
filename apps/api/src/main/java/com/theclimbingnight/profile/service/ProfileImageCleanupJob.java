package com.theclimbingnight.profile.service;

import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정하지 않고 24시간이 지난 UPLOADING 행을 지운다(작업이 없다). 업로드 객체는 버킷 수명 주기가 지운다.
 * 지우기 직전에 행을 잠그고 다시 UPLOADING인지 봐서, 막 확정된 사진을 지우지 않는다.
 */
@Component
public class ProfileImageCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(ProfileImageCleanupJob.class);
	static final Duration MAX_AGE = Duration.ofHours(24);

	private final ProfileImageRepository images;
	private final Clock clock;

	public ProfileImageCleanupJob(ProfileImageRepository images, Clock clock) {
		this.images = images;
		this.clock = clock;
	}

	@Scheduled(cron = "0 20 * * * *", zone = "Asia/Seoul")
	@Transactional
	public void run() {
		Instant cutoff = clock.instant().minus(MAX_AGE);
		List<Long> ids = images.findIdsByStatusCreatedBefore(ProfileImageStatus.UPLOADING, cutoff);
		int removed = 0;
		for (Long id : ids) {
			ProfileImage image = images.findByIdForUpdate(id).orElse(null);
			if (image == null || image.getStatus() != ProfileImageStatus.UPLOADING) {
				continue;
			}
			images.delete(image);
			removed++;
		}
		if (removed > 0) {
			log.info("오래된 프로필 사진 업로드 {}개 삭제", removed);
		}
	}
}
