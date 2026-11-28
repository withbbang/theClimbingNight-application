package com.theclimbingnight.profile.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "profile_images")
public class ProfileImage extends BaseTimeEntity {

	public static final String LARGE_SUFFIX = "/l.webp";
	public static final String SMALL_SUFFIX = "/s.webp";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ProfileImageStatus status;

	@Column(name = "upload_key", nullable = false, length = 128)
	private String uploadKey;

	/** 공개 사본 폴더 `a/{hex}`. 표시 키는 여기에 접미사를 붙인다. */
	@Column(name = "public_key", nullable = false, length = 128)
	private String publicKey;

	@Column(name = "original_path", length = 255)
	private String originalPath;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	public static ProfileImage start(long userId, String uploadKey, String publicKey, long byteSize) {
		ProfileImage image = new ProfileImage();
		image.userId = userId;
		image.status = ProfileImageStatus.UPLOADING;
		image.uploadKey = uploadKey;
		image.publicKey = publicKey;
		image.byteSize = byteSize;
		return image;
	}

	public String largeKey() {
		return publicKey + LARGE_SUFFIX;
	}

	public String smallKey() {
		return publicKey + SMALL_SUFFIX;
	}

	public void markProcessing() {
		this.status = ProfileImageStatus.PROCESSING;
	}

	public void markReady() {
		this.status = ProfileImageStatus.READY;
	}

	public void markFailed() {
		this.status = ProfileImageStatus.FAILED;
	}

	public void markRemoved() {
		this.status = ProfileImageStatus.REMOVED;
	}

	public void recordOriginal(String originalPath) {
		this.originalPath = originalPath;
	}

	public void clearOriginal() {
		this.originalPath = null;
	}
}
