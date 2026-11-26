package com.theclimbingnight.media.worker;

import com.theclimbingnight.common.FailureSummary;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 업로드를 받아 NAS에 원본을 두고 WebP 두 장을 공개 버킷에 올린다.
 * 게시물 상태는 공개본을 올리기 전과 후에 다시 본다. 올린 뒤의 확인은 게시물 행을 잠근 트랜잭션에서 사진 READY 기록과 함께
 * 해서, 숨김·삭제(같은 행을 잠근다)와 엇갈려도 "숨겼는데 공개본이 남는" 일이 없다. 숨겨졌으면 방금 올린 두 장을 지운다.
 */
public class ProcessJobHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(ProcessJobHandler.class);
	static final String WEBP = "image/webp";

	private final MediaStorage storage;
	private final OriginalStore originals;
	private final ImageTranscoder transcoder;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final TransactionTemplate tx;
	private final Clock clock;

	public ProcessJobHandler(MediaStorage storage, OriginalStore originals, ImageTranscoder transcoder,
			PostRepository posts, PostMediaRepository mediaRepository, TransactionTemplate tx, Clock clock) {
		this.storage = storage;
		this.originals = originals;
		this.transcoder = transcoder;
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.PROCESS;
	}

	@Override
	public void handle(MediaJob job) throws IOException {
		PostMedia media = tx.execute(s -> mediaRepository.findById(job.getTargetId()).orElse(null));
		if (media == null) {
			return;
		}
		if (media.getStatus() == PostMediaStatus.READY) {
			// 지난 시도가 READY까지 기록하고 업로드 삭제에서 실패했다.
			storage.deleteUpload(media.getUploadKey());
			return;
		}
		if (!postPending(media.getPostId())) {
			return;
		}
		processFromUpload(media, media.getPublicKey());
	}

	/** 업로드에서 원본을 저장하고 변환해 publicKey에 올린다. 올렸으면 업로드를 지우고 true. */
	boolean processFromUpload(PostMedia media, String publicKey) throws IOException {
		Path workDir = Files.createTempDirectory("media-" + media.getId() + "-");
		try {
			Path source = Files.createTempFile(workDir, "source-", ".jpg");
			try (InputStream in = storage.openUpload(media.getUploadKey())) {
				Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
			}
			String originalPath;
			try (InputStream in = Files.newInputStream(source)) {
				originalPath = originals.save(media.getId(), in, clock.instant());
			}
			tx.executeWithoutResult(s -> mediaRepository.findById(media.getId())
					.ifPresent(m -> m.recordOriginal(originalPath)));
			boolean published = publish(media.getId(), source, originalPath, publicKey, workDir);
			if (published) {
				storage.deleteUpload(media.getUploadKey());
			}
			return published;
		} finally {
			deleteRecursively(workDir);
		}
	}

	/** NAS 원본에서 변환해 publicKey에 올린다. 올렸으면 true. */
	boolean processFromOriginal(PostMedia media, String publicKey) throws IOException {
		Path workDir = Files.createTempDirectory("media-" + media.getId() + "-");
		try {
			Path source = Files.createTempFile(workDir, "source-", ".jpg");
			try (InputStream in = originals.open(media.getOriginalPath())) {
				Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
			}
			return publish(media.getId(), source, media.getOriginalPath(), publicKey, workDir);
		} finally {
			deleteRecursively(workDir);
		}
	}

	private boolean publish(long mediaId, Path source, String originalPath, String publicKey, Path workDir)
			throws IOException {
		TranscodedImage image = transcoder.transcode(source, workDir);
		Boolean stillPending = tx.execute(s -> mediaRepository.findById(mediaId)
				.map(m -> isPending(posts.findById(m.getPostId()).orElse(null))).orElse(false));
		if (!Boolean.TRUE.equals(stillPending)) {
			return false;
		}
		String display = publicKey + PostMedia.DISPLAY_SUFFIX;
		String thumb = publicKey + PostMedia.THUMB_SUFFIX;
		Boolean committed;
		try {
			storage.putPublic(display, image.display(), WEBP);
			storage.putPublic(thumb, image.thumb(), WEBP);
			long byteSize = Files.size(image.display());
			committed = tx.execute(s -> markReadyIfPending(mediaId, originalPath, publicKey, image, byteSize));
		} catch (IOException | RuntimeException e) {
			// READY를 기록하지 못했다. 복원(RELEASE)은 시도마다 새 키를 쓰므로 여기서 지우지 않으면 아무도 지우지 않는다.
			deletePublicQuietly(storage, publicKey, e);
			throw e;
		}
		if (!Boolean.TRUE.equals(committed)) {
			storage.deletePublic(display);
			storage.deletePublic(thumb);
			return false;
		}
		return true;
	}

	/** 실패 처리 중의 정리. 정리 실패는 원래 예외에 덧붙이고 원래 예외를 그대로 던지게 한다. */
	static void deletePublicQuietly(MediaStorage storage, String publicKey, Exception cause) {
		for (String key : List.of(publicKey + PostMedia.DISPLAY_SUFFIX, publicKey + PostMedia.THUMB_SUFFIX)) {
			try {
				storage.deletePublic(key);
			} catch (RuntimeException cleanup) {
				cause.addSuppressed(cleanup);
			}
		}
	}

	/** 게시물 행을 잠근 채 아직 PENDING인지 보고, 맞으면 사진을 READY로, 사진이 모두 READY면 게시물을 보이게 한다. */
	private boolean markReadyIfPending(long mediaId, String originalPath, String publicKey, TranscodedImage image,
			long byteSize) {
		PostMedia media = mediaRepository.findById(mediaId).orElse(null);
		if (media == null) {
			return false;
		}
		Post post = posts.findByIdForUpdate(media.getPostId()).orElse(null);
		if (!isPending(post)) {
			return false;
		}
		media.markReady(originalPath, image.width(), image.height(), byteSize);
		media.movePublicKey(publicKey);
		markVisibleIfAllReady(post, mediaRepository, clock);
		return true;
	}

	static void markVisibleIfAllReady(Post post, PostMediaRepository mediaRepository, Clock clock) {
		boolean allReady = mediaRepository.findAllByPostIdOrderBySortOrder(post.getId()).stream()
				.allMatch(m -> m.getStatus() == PostMediaStatus.READY);
		if (allReady) {
			post.markVisible(clock.instant());
		}
	}

	static boolean isPending(Post post) {
		return post != null && post.getDeletedAt() == null && post.getStatus() == PostStatus.PENDING;
	}

	private boolean postPending(long postId) {
		return Boolean.TRUE.equals(tx.execute(s -> isPending(posts.findById(postId).orElse(null))));
	}

	/** 임시 폴더 정리 실패는 로그만 남긴다. 원래 예외를 덮거나 성공한 작업을 실패로 바꾸지 않는다. */
	private static void deleteRecursively(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		} catch (IOException | RuntimeException e) {
			log.warn("임시 폴더 삭제 실패 {} {}", dir, FailureSummary.of(e));
		}
	}
}
