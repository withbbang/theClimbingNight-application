package com.theclimbingnight.media.worker;

import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 내린 프로필 사진의 공개·격리·업로드 객체를 모두 지운다. R2(S3) 삭제는 객체가 없어도 성공한다.
 * 공개본은 원본을 저장한 뒤에 올리므로, 원본 경로가 있는 사진만 CDN 캐시를 비운다. NAS 원본은 PURGE_ORIGINAL이 지운다.
 */
public class ProfileDeleteMediaJobHandler implements JobHandler {

	private final MediaStorage storage;
	private final CachePurger purger;
	private final MediaUrls urls;
	private final ProfileImageRepository images;
	private final TransactionTemplate tx;

	public ProfileDeleteMediaJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			ProfileImageRepository images, TransactionTemplate tx) {
		this.storage = storage;
		this.purger = purger;
		this.urls = urls;
		this.images = images;
		this.tx = tx;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.DELETE_MEDIA;
	}

	@Override
	public MediaTargetType targetType() {
		return MediaTargetType.PROFILE_IMAGE;
	}

	@Override
	public void handle(MediaJob job) {
		ProfileImage image = tx.execute(s -> images.findById(job.getTargetId()).orElse(null));
		if (image == null) {
			return;
		}
		String large = image.largeKey();
		String small = image.smallKey();
		storage.deletePublic(large);
		storage.deletePublic(small);
		storage.deleteQuarantine(large);
		storage.deleteQuarantine(small);
		storage.deleteUpload(image.getUploadKey());
		if (image.getOriginalPath() != null) {
			purger.purge(List.of(urls.publicUrl(large), urls.publicUrl(small)));
		}
	}
}
