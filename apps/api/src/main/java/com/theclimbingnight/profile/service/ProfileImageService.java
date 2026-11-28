package com.theclimbingnight.profile.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.PresignedUpload;
import com.theclimbingnight.profile.dto.ProfileImageStatusResponse;
import com.theclimbingnight.profile.dto.ProfileImageUploadResponse;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모든 쓰기는 사용자 행을 먼저 잠근 뒤 사진 행을 잠근다(탈퇴와 같은 순서). */
@Service
@Transactional(readOnly = true)
public class ProfileImageService {

	static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
	static final int MAX_PROCESSING = 2;
	private static final String JPEG = "image/jpeg";
	private static final int KEY_BYTES = 16;

	private final ProfileImageRepository images;
	private final UserRepository users;
	private final MediaStorage storage;
	private final MediaJobScheduler scheduler;
	private final SecureRandom random;

	public ProfileImageService(ProfileImageRepository images, UserRepository users, MediaStorage storage,
			MediaJobScheduler scheduler, SecureRandom random) {
		this.images = images;
		this.users = users;
		this.storage = storage;
		this.scheduler = scheduler;
		this.random = random;
	}

	/** 예전 UPLOADING 행(작업이 없다)을 지우고 새 행과 업로드 주소를 준다. */
	@Transactional
	public ProfileImageUploadResponse start(long userId, long byteSize) {
		lockUser(userId);
		images.deleteByUserIdAndStatus(userId, ProfileImageStatus.UPLOADING);
		if (images.countByUserIdAndStatus(userId, ProfileImageStatus.PROCESSING) >= MAX_PROCESSING) {
			throw new BusinessException(ErrorCode.PROFILE_IMAGE_BUSY);
		}
		String hex = randomHex();
		ProfileImage image = images.save(ProfileImage.start(userId, "uploads/" + hex + ".jpg", "a/" + randomHex(), byteSize));
		PresignedUpload upload = storage.presignUpload(image.getUploadKey(), JPEG, byteSize, UPLOAD_TTL);
		return new ProfileImageUploadResponse(image.getId(), upload.url(), upload.headers(),
				upload.expiresAt().atOffset(ZoneOffset.UTC));
	}

	@Transactional
	public ProfileImageStatusResponse confirm(long userId, long imageId) {
		lockUser(userId);
		ProfileImage image = images.findByIdForUpdate(imageId).filter(i -> i.getUserId() == userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (image.getStatus() != ProfileImageStatus.UPLOADING) {
			return toStatus(image);
		}
		OptionalLong uploaded = storage.uploadedSize(image.getUploadKey());
		if (uploaded.isEmpty() || uploaded.getAsLong() != image.getByteSize()) {
			throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_INCOMPLETE);
		}
		image.markProcessing();
		scheduler.enqueueProfileProcess(image);
		return toStatus(image);
	}

	public ProfileImageStatusResponse status(long userId, long imageId) {
		return images.findById(imageId).filter(i -> i.getUserId() == userId).map(this::toStatus)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	/** 지금 사진과 처리 중인 사진을 모두 내려, 늦게 끝난 처리가 되돌린 사진을 다시 채우지 못하게 한다. */
	@Transactional
	public void reset(long userId) {
		User user = lockUser(userId);
		Set<Long> dropped = new HashSet<>();
		List<ProfileImage> processing = images.findAllByUserIdAndStatus(userId, ProfileImageStatus.PROCESSING);
		for (ProfileImage image : processing) {
			drop(image, dropped);
		}
		Long currentId = user.getProfileImageId();
		if (currentId != null) {
			images.findById(currentId).ifPresent(image -> drop(image, dropped));
		}
		user.useProfileImage(null);
	}

	private void drop(ProfileImage image, Set<Long> dropped) {
		if (image.getStatus() == ProfileImageStatus.REMOVED || !dropped.add(image.getId())) {
			return;
		}
		image.markRemoved();
		scheduler.onProfileImageDropped(image);
	}

	private User lockUser(long userId) {
		return users.findByIdForUpdate(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
	}

	private ProfileImageStatusResponse toStatus(ProfileImage image) {
		return new ProfileImageStatusResponse(image.getId(), image.getStatus().name());
	}

	private String randomHex() {
		byte[] bytes = new byte[KEY_BYTES];
		random.nextBytes(bytes);
		return java.util.HexFormat.of().formatHex(bytes);
	}
}
