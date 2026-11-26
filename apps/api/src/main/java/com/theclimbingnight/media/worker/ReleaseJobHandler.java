package com.theclimbingnight.media.worker;

import com.theclimbingnight.common.FailureSummary;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 복원된(PENDING) 글의 사진을 새 공개 키로 되살린다. 예전 URL은 되살아나지 않는다.
 * <ol>
 * <li>격리본이 있으면 새 키로 복사하고 예전 격리본을 지운다.</li>
 * <li>없으면(처리 전에 숨겨졌거나 처리 작업이 취소됨) NAS 원본으로, 그것도 없으면 아직 남은 업로드로 다시 변환한다.</li>
 * <li>아무것도 없으면 사진과 게시물을 FAILED로 둔다.</li>
 * </ol>
 * 복원은 사진을 PROCESSING으로 돌려 둔다. 사진 하나를 옮기면 READY로 기록하고, 모두 READY가 되면 글이 보인다.
 * 이미 READY면 이번 복원에서 옮긴 사진이니 건드리지 않는다(지금 쓰는 공개본을 지우지 않게).
 * 공개본을 올린 뒤에는 처리 작업과 같이 게시물 행을 잠그고 다시 PENDING인지 본다. 그새 다시 숨겼으면 새 공개본을 지운다.
 * 어느 경로든 먼저 예전 키의 공개본을 지우고 그 URL의 캐시를 비운다(격리가 중간에 실패했을 때 남은 사본). 이 정리는 안전망이라
 * 실패해도 로그만 남기고 복원을 계속한다. 재시도 끝에 멀쩡한 격리본이 있는 글을 FAILED로 만들지 않는다.
 */
public class ReleaseJobHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(ReleaseJobHandler.class);
	private static final int KEY_BYTES = 16;

	private final MediaStorage storage;
	private final CachePurger purger;
	private final MediaUrls urls;
	private final OriginalStore originals;
	private final ProcessJobHandler process;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final SecureRandom random;

	public ReleaseJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls, OriginalStore originals,
			ProcessJobHandler process, PostRepository posts, PostMediaRepository mediaRepository,
			TransactionTemplate tx, Clock clock, SecureRandom random) {
		this.storage = storage;
		this.purger = purger;
		this.urls = urls;
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
		if (media == null || media.getStatus() == PostMediaStatus.READY || !postPending(media.getPostId())) {
			return;
		}
		String oldKey = media.getPublicKey();
		String newKey = "p/" + randomHex();
		// 격리 작업이 복사 뒤 공개본 삭제 전에 실패했으면 예전 공개본(이미 알려진 URL)이 남아 있다. 새 사본을 만들기 전에 지운다.
		// 없어도 지우기는 성공한다.
		deleteOldPublic(oldKey);
		if (hasQuarantineCopy(oldKey)) {
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
			deleteOldQuarantine(oldKey);
		}
	}

	private void restoreFromQuarantine(long mediaId, String oldKey, String newKey) {
		Boolean committed;
		try {
			storage.copyQuarantineToPublic(oldKey + PostMedia.DISPLAY_SUFFIX, newKey + PostMedia.DISPLAY_SUFFIX);
			storage.copyQuarantineToPublic(oldKey + PostMedia.THUMB_SUFFIX, newKey + PostMedia.THUMB_SUFFIX);
			committed = tx.execute(s -> moveIfPending(mediaId, newKey));
		} catch (RuntimeException e) {
			// 시도마다 새 키라 여기서 지우지 않으면 이 사본은 아무도 지우지 않는다.
			ProcessJobHandler.deletePublicQuietly(storage, newKey, e);
			throw e;
		}
		if (!Boolean.TRUE.equals(committed)) {
			storage.deletePublic(newKey + PostMedia.DISPLAY_SUFFIX);
			storage.deletePublic(newKey + PostMedia.THUMB_SUFFIX);
			return;
		}
		deleteOldQuarantine(oldKey);
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
		media.markRestored(newKey);
		ProcessJobHandler.markVisibleIfAllReady(post, mediaRepository, clock);
		return true;
	}

	private void markFailed(long mediaId) {
		mediaRepository.findById(mediaId).ifPresent(media -> {
			media.markFailed();
			posts.findByIdForUpdate(media.getPostId()).filter(ProcessJobHandler::isPending).ifPresent(Post::markFailed);
		});
	}

	/** 정상 흐름에서는 격리 작업이 이미 지우고 비웠다. 실패해도 복원을 막지 않는다. */
	private void deleteOldPublic(String oldKey) {
		String display = oldKey + PostMedia.DISPLAY_SUFFIX;
		String thumb = oldKey + PostMedia.THUMB_SUFFIX;
		try {
			storage.deletePublic(display);
			storage.deletePublic(thumb);
			purger.purge(List.of(urls.publicUrl(display), urls.publicUrl(thumb)));
		} catch (RuntimeException e) {
			log.warn("복원 전 예전 공개본 정리 실패 {} {}", oldKey, FailureSummary.of(e));
		}
	}

	private void deleteOldQuarantine(String oldKey) {
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
