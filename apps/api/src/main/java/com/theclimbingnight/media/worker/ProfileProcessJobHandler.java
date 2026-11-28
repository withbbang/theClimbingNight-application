package com.theclimbingnight.media.worker;

import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.job.MediaTargetType;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로필 사진 업로드를 받아 NAS에 원본을 두고 정사각 WebP 두 장을 공개 버킷에 올린 뒤, 지금 사진으로 쓸지 정한다.
 * <p>
 * 정하는 트랜잭션은 사용자 행 → 사진 행 순서로 잠근다. 되돌리기·탈퇴·업로드 시작·확정도 사용자 행을 먼저 잠그므로 이들과
 * 차례로 돈다. 되돌리기는 처리 중인 사진을 사진 행 잠금 없이 읽고 REMOVED로 바꾸기 때문에, 사용자 행 잠금이 없으면
 * 되돌린 사진이 READY로 덮일 수 있다.
 * <ul>
 * <li>사진이 이미 PROCESSING이 아니면(되돌리기·탈퇴가 REMOVED로 바꾸고 정리 작업을 만들었다) 그 정리 작업이 이미 돌았을 수
 * 있으므로 방금 올린 두 장을 직접 지운다.</li>
 * <li>사용자가 활성이 아니거나 더 새 사진이 있으면 이 사진을 내리고 정리를 작업에 맡긴다. 늦게 끝난 예전 업로드가 새 사진을
 * 덮지 않게 한다.</li>
 * <li>그 밖이면 READY로 두고 지금 사진으로 바꾼다. 예전 지금 사진은 내리고 정리 작업을 만든다.</li>
 * </ul>
 */
public class ProfileProcessJobHandler implements JobHandler {

	private enum Outcome { READY, DROPPED, STALE }

	private final MediaStorage storage;
	private final OriginalStore originals;
	private final ImageTranscoder transcoder;
	private final UserRepository users;
	private final ProfileImageRepository images;
	private final MediaJobScheduler scheduler;
	private final TransactionTemplate tx;
	private final Clock clock;

	public ProfileProcessJobHandler(MediaStorage storage, OriginalStore originals, ImageTranscoder transcoder,
			UserRepository users, ProfileImageRepository images, MediaJobScheduler scheduler, TransactionTemplate tx,
			Clock clock) {
		this.storage = storage;
		this.originals = originals;
		this.transcoder = transcoder;
		this.users = users;
		this.images = images;
		this.scheduler = scheduler;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public MediaJobKind kind() {
		return MediaJobKind.PROCESS;
	}

	@Override
	public MediaTargetType targetType() {
		return MediaTargetType.PROFILE_IMAGE;
	}

	@Override
	public void handle(MediaJob job) throws IOException {
		ProfileImage image = tx.execute(s -> images.findById(job.getTargetId()).orElse(null));
		if (image == null) {
			return;
		}
		if (image.getStatus() == ProfileImageStatus.READY) {
			// 지난 시도가 READY까지 기록하고 업로드 삭제에서 실패했다.
			storage.deleteUpload(image.getUploadKey());
			return;
		}
		if (image.getStatus() != ProfileImageStatus.PROCESSING) {
			return;
		}
		Path workDir = Files.createTempDirectory("profile-" + image.getId() + "-");
		try {
			process(image, workDir);
		} finally {
			ProcessJobHandler.deleteRecursively(workDir);
		}
	}

	private void process(ProfileImage image, Path workDir) throws IOException {
		long imageId = image.getId();
		long userId = image.getUserId();
		Path source = Files.createTempFile(workDir, "source-", ".jpg");
		try (InputStream in = storage.openUpload(image.getUploadKey())) {
			Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
		}
		String originalPath;
		try (InputStream in = Files.newInputStream(source)) {
			originalPath = originals.saveProfile(imageId, in, clock.instant());
		}
		// 그새 내려졌어도 경로를 남겨 PURGE_ORIGINAL이 지우게 한다. 잠가서 읽어야 내린 상태를 덮어쓰지 않는다.
		tx.executeWithoutResult(s -> {
			users.findByIdForUpdate(userId);
			images.findByIdForUpdate(imageId).ifPresent(i -> i.recordOriginal(originalPath));
		});
		ImageTranscoder.SquareImage square = transcoder.transcodeSquare(source, workDir);
		List<String> keys = List.of(image.largeKey(), image.smallKey());
		Outcome outcome;
		try {
			storage.putPublic(image.largeKey(), square.large(), ProcessJobHandler.WEBP);
			storage.putPublic(image.smallKey(), square.small(), ProcessJobHandler.WEBP);
			outcome = tx.execute(s -> decide(userId, imageId));
		} catch (RuntimeException e) {
			// 지금 사진으로 기록하지 못했다. 재시도는 같은 키에 다시 올린다.
			ProcessJobHandler.deletePublicQuietly(storage, keys, e);
			throw e;
		}
		if (outcome == Outcome.STALE) {
			keys.forEach(storage::deletePublic);
			return;
		}
		if (outcome == Outcome.READY) {
			storage.deleteUpload(image.getUploadKey());
		}
	}

	private Outcome decide(long userId, long imageId) {
		User user = users.findByIdForUpdate(userId).orElse(null);
		ProfileImage image = images.findByIdForUpdate(imageId).orElse(null);
		if (image == null || image.getStatus() != ProfileImageStatus.PROCESSING) {
			return Outcome.STALE;
		}
		if (user == null || !user.isActive() || newerExists(userId, imageId)) {
			image.markRemoved();
			scheduler.onProfileImageDropped(image);
			return Outcome.DROPPED;
		}
		image.markReady();
		Long currentId = user.getProfileImageId();
		if (currentId != null && currentId != imageId) {
			images.findByIdForUpdate(currentId).filter(old -> old.getStatus() != ProfileImageStatus.REMOVED)
					.ifPresent(old -> {
						old.markRemoved();
						scheduler.onProfileImageDropped(old);
					});
		}
		user.useProfileImage(imageId);
		return Outcome.READY;
	}

	private boolean newerExists(long userId, long imageId) {
		return images.existsByUserIdAndStatusAndIdGreaterThan(userId, ProfileImageStatus.PROCESSING, imageId)
				|| images.existsByUserIdAndStatusAndIdGreaterThan(userId, ProfileImageStatus.READY, imageId);
	}
}
