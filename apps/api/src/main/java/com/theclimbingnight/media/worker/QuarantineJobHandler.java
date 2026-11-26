package com.theclimbingnight.media.worker;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 숨긴 글의 공개본을 비공개 버킷 quarantine/으로 옮기고(복사 후 삭제) CDN 캐시를 비운다.
 * 아직 처리 전인 사진(READY 아님)은 공개본이 없으니 할 일이 없다. 그새 복원된 글이면 새 키의 공개본을 건드리지 않게 멈춘다.
 * 다시 돌아도 같은 결과가 되도록, 격리본이 이미 있으면 복사를 건너뛴다.
 */
public class QuarantineJobHandler implements JobHandler {

	private final MediaStorage storage;
	private final CachePurger purger;
	private final MediaUrls urls;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final TransactionTemplate tx;

	public QuarantineJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls, PostRepository posts,
			PostMediaRepository mediaRepository, TransactionTemplate tx) {
		this.storage = storage;
		this.purger = purger;
		this.urls = urls;
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.tx = tx;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.QUARANTINE;
	}

	@Override
	public void handle(MediaJob job) {
		PostMedia media = tx.execute(s -> mediaRepository.findById(job.getTargetId()).orElse(null));
		if (media == null || !postHidden(media.getPostId()) || media.getStatus() != PostMediaStatus.READY) {
			return;
		}
		String display = media.displayKey();
		String thumb = media.thumbKey();
		if (!(storage.quarantineExists(display) && storage.quarantineExists(thumb))) {
			storage.copyPublicToQuarantine(display);
			storage.copyPublicToQuarantine(thumb);
		}
		storage.deletePublic(display);
		storage.deletePublic(thumb);
		purger.purge(List.of(urls.publicUrl(display), urls.publicUrl(thumb)));
	}

	private boolean postHidden(long postId) {
		return Boolean.TRUE.equals(tx.execute(s -> {
			Post post = posts.findById(postId).orElse(null);
			return post != null && post.getDeletedAt() == null
					&& (post.getStatus() == PostStatus.HIDDEN || post.getStatus() == PostStatus.HIDDEN_PENDING);
		}));
	}
}
