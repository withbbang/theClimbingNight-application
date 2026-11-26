package com.theclimbingnight.media.worker;

import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지운 글의 공개·격리·업로드 객체를 모두 지운다. R2(S3) 삭제는 객체가 없어도 성공하므로 초안 사진(UPLOADING)에도 쓴다.
 * 공개된 적이 있는(READY) 사진만 CDN 캐시를 비운다. NAS 원본은 PURGE_ORIGINAL이 30일 뒤에 지운다.
 */
public class DeleteMediaJobHandler implements JobHandler {

	private final MediaStorage storage;
	private final CachePurger purger;
	private final MediaUrls urls;
	private final PostMediaRepository mediaRepository;
	private final TransactionTemplate tx;

	public DeleteMediaJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			PostMediaRepository mediaRepository, TransactionTemplate tx) {
		this.storage = storage;
		this.purger = purger;
		this.urls = urls;
		this.mediaRepository = mediaRepository;
		this.tx = tx;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.DELETE_MEDIA;
	}

	@Override
	public void handle(MediaJob job) {
		PostMedia media = tx.execute(s -> mediaRepository.findById(job.getTargetId()).orElse(null));
		if (media == null) {
			return;
		}
		String display = media.displayKey();
		String thumb = media.thumbKey();
		storage.deletePublic(display);
		storage.deletePublic(thumb);
		storage.deleteQuarantine(display);
		storage.deleteQuarantine(thumb);
		storage.deleteUpload(media.getUploadKey());
		if (media.getStatus() == PostMediaStatus.READY) {
			purger.purge(List.of(urls.publicUrl(display), urls.publicUrl(thumb)));
		}
	}
}
