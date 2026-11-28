package com.theclimbingnight.media.worker;

import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영진이 내린(REMOVED) 프로필 사진의 공개본을 비공개 버킷 quarantine/으로 옮기고(복사 후 삭제) CDN 캐시를 비운다.
 * 남아 있는 공개본만(한 장만 남았어도) 격리본이 없을 때 복사한다. 다시 돌아도 같은 결과가 되도록 격리본이 이미 있으면
 * 복사를 건너뛰고, 공개본도 격리본도 없으면(공개된 적이 없다) 아무것도 안 한다.
 */
public class ProfileQuarantineJobHandler implements JobHandler {

	private final MediaStorage storage;
	private final CachePurger purger;
	private final MediaUrls urls;
	private final ProfileImageRepository images;
	private final TransactionTemplate tx;

	public ProfileQuarantineJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			ProfileImageRepository images, TransactionTemplate tx) {
		this.storage = storage;
		this.purger = purger;
		this.urls = urls;
		this.images = images;
		this.tx = tx;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.QUARANTINE;
	}

	@Override
	public MediaTargetType targetType() {
		return MediaTargetType.PROFILE_IMAGE;
	}

	@Override
	public void handle(MediaJob job) {
		ProfileImage image = tx.execute(s -> images.findById(job.getTargetId()).orElse(null));
		if (image == null || image.getStatus() != ProfileImageStatus.REMOVED) {
			return;
		}
		String large = image.largeKey();
		String small = image.smallKey();
		boolean found = false;
		for (String key : List.of(large, small)) {
			boolean quarantined = storage.quarantineExists(key);
			if (!quarantined && storage.publicExists(key)) {
				storage.copyPublicToQuarantine(key);
				quarantined = true;
			}
			found |= quarantined;
		}
		if (!found) {
			return;
		}
		storage.deletePublic(large);
		storage.deletePublic(small);
		purger.purge(List.of(urls.publicUrl(large), urls.publicUrl(small)));
	}
}
