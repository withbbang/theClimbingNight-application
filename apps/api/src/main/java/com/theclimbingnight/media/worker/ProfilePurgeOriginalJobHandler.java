package com.theclimbingnight.media.worker;

import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.io.IOException;
import org.springframework.transaction.support.TransactionTemplate;

/** 내린 프로필 사진의 NAS 원본을 지우고 original_path를 비운다. 파일이 이미 없어도 성공한다. */
public class ProfilePurgeOriginalJobHandler implements JobHandler {

	private final OriginalStore originals;
	private final ProfileImageRepository images;
	private final TransactionTemplate tx;

	public ProfilePurgeOriginalJobHandler(OriginalStore originals, ProfileImageRepository images,
			TransactionTemplate tx) {
		this.originals = originals;
		this.images = images;
		this.tx = tx;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.PURGE_ORIGINAL;
	}

	@Override
	public MediaTargetType targetType() {
		return MediaTargetType.PROFILE_IMAGE;
	}

	@Override
	public void handle(MediaJob job) throws IOException {
		ProfileImage image = tx.execute(s -> images.findById(job.getTargetId()).orElse(null));
		if (image == null || image.getOriginalPath() == null) {
			return;
		}
		originals.delete(image.getOriginalPath());
		tx.executeWithoutResult(s -> images.findById(image.getId()).ifPresent(ProfileImage::clearOriginal));
	}
}
