package com.theclimbingnight.media.worker;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 복원된(PENDING) 글의 사진을 새 공개 키로 되살린다. 예전 URL은 되살아나지 않는다.
 * <ol>
 * <li>격리본이 있으면 새 키로 복사하고 예전 격리본을 지운다.</li>
 * <li>없으면(처리 전에 숨겨졌거나 처리 작업이 취소됨) NAS 원본으로, 그것도 없으면 아직 남은 업로드로 다시 변환한다.</li>
 * <li>아무것도 없으면 사진과 게시물을 FAILED로 둔다.</li>
 * </ol>
 * 공개본을 올린 뒤에는 처리 작업과 같이 게시물 행을 잠그고 다시 PENDING인지 본다. 그새 다시 숨겼으면 새 공개본을 지운다.
 */
public class ReleaseJobHandler implements JobHandler {

	private static final int KEY_BYTES = 16;

	private final MediaStorage storage;
	private final OriginalStore originals;
	private final ProcessJobHandler process;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final SecureRandom random;

	public ReleaseJobHandler(MediaStorage storage, OriginalStore originals, ProcessJobHandler process,
			PostRepository posts, PostMediaRepository mediaRepository, TransactionTemplate tx, Clock clock,
			SecureRandom random) {
		this.storage = storage;
		this.originals = originals;
		this.process = process;
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.tx = tx;
		this.clock = clock;
		this.random = random;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.RELEASE;
	}

	@Override
	public void handle(MediaJob job) throws IOException {
		PostMedia media = tx.execute(s -> mediaRepository.findById(job.getTargetId()).orElse(null));
		if (media == null || !postPending(media.getPostId())) {
			return;
		}
		String oldKey = media.getPublicKey();
		String newKey = "p/" + randomHex();
		if (media.getStatus() == PostMediaStatus.READY && hasQuarantineCopy(oldKey)) {
			restoreFromQuarantine(media.getId(), oldKey, newKey);
			return;
		}
		boolean published;
		if (media.getOriginalPath() != null && originals.exists(media.getOriginalPath())) {
			published = process.processFromOriginal(media, newKey);
		} else if (storage.uploadedSize(media.getUploadKey()).isPresent()) {
			published = process.processFromUpload(media, newKey);
		} else {
			tx.executeWithoutResult(s -> markFailed(media.getId()));
			return;
		}
		if (published) {
			// 격리에 실패해 예전 공개본이 남아 있었을 수 있다. 없어도 지우기는 성공한다.
			deleteOld(oldKey);
		}
	}

	private void restoreFromQuarantine(long mediaId, String oldKey, String newKey) {
		storage.copyQuarantineToPublic(oldKey + PostMedia.DISPLAY_SUFFIX, newKey + PostMedia.DISPLAY_SUFFIX);
		storage.copyQuarantineToPublic(oldKey + PostMedia.THUMB_SUFFIX, newKey + PostMedia.THUMB_SUFFIX);
		Boolean committed = tx.execute(s -> moveIfPending(mediaId, newKey));
		if (!Boolean.TRUE.equals(committed)) {
			storage.deletePublic(newKey + PostMedia.DISPLAY_SUFFIX);
			storage.deletePublic(newKey + PostMedia.THUMB_SUFFIX);
			return;
		}
		storage.deleteQuarantine(oldKey + PostMedia.DISPLAY_SUFFIX);
		storage.deleteQuarantine(oldKey + PostMedia.THUMB_SUFFIX);
	}

	private boolean moveIfPending(long mediaId, String newKey) {
		PostMedia media = mediaRepository.findById(mediaId).orElse(null);
		if (media == null) {
			return false;
		}
		Post post = posts.findByIdForUpdate(media.getPostId()).orElse(null);
		if (!ProcessJobHandler.isPending(post)) {
			return false;
		}
		media.movePublicKey(newKey);
		ProcessJobHandler.markVisibleIfAllReady(post, mediaRepository, clock);
		return true;
	}

	private void markFailed(long mediaId) {
		mediaRepository.findById(mediaId).ifPresent(media -> {
			media.markFailed();
			posts.findByIdForUpdate(media.getPostId()).filter(ProcessJobHandler::isPending).ifPresent(Post::markFailed);
		});
	}

	private void deleteOld(String oldKey) {
		storage.deletePublic(oldKey + PostMedia.DISPLAY_SUFFIX);
		storage.deletePublic(oldKey + PostMedia.THUMB_SUFFIX);
		storage.deleteQuarantine(oldKey + PostMedia.DISPLAY_SUFFIX);
		storage.deleteQuarantine(oldKey + PostMedia.THUMB_SUFFIX);
	}

	private boolean hasQuarantineCopy(String key) {
		return storage.quarantineExists(key + PostMedia.DISPLAY_SUFFIX)
				&& storage.quarantineExists(key + PostMedia.THUMB_SUFFIX);
	}

	private boolean postPending(long postId) {
		return Boolean.TRUE.equals(tx.execute(s -> ProcessJobHandler.isPending(posts.findById(postId).orElse(null))));
	}

	private String randomHex() {
		byte[] bytes = new byte[KEY_BYTES];
		random.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
