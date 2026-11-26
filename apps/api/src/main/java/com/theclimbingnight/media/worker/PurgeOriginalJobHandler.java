package com.theclimbingnight.media.worker;

import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import java.io.IOException;
import org.springframework.transaction.support.TransactionTemplate;

/** 지운 글의 NAS 원본을 지우고 사진 행의 original_path를 비운다. 파일이 이미 없어도 성공한다. */
public class PurgeOriginalJobHandler implements JobHandler {

	private final OriginalStore originals;
	private final PostMediaRepository mediaRepository;
	private final TransactionTemplate tx;

	public PurgeOriginalJobHandler(OriginalStore originals, PostMediaRepository mediaRepository,
			TransactionTemplate tx) {
		this.originals = originals;
		this.mediaRepository = mediaRepository;
		this.tx = tx;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.PURGE_ORIGINAL;
	}

	@Override
	public void handle(MediaJob job) throws IOException {
		PostMedia media = tx.execute(s -> mediaRepository.findById(job.getTargetId()).orElse(null));
		if (media == null || media.getOriginalPath() == null) {
			return;
		}
		originals.delete(media.getOriginalPath());
		tx.executeWithoutResult(s -> mediaRepository.findById(media.getId()).ifPresent(PostMedia::clearOriginal));
	}
}
