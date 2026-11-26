package com.theclimbingnight.feed.entity;

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
@Table(name = "post_media")
public class PostMedia extends BaseTimeEntity {

	public static final String DISPLAY_SUFFIX = "/d.webp";
	public static final String THUMB_SUFFIX = "/t.webp";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MediaType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PostMediaStatus status;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "upload_key", nullable = false, length = 128)
	private String uploadKey;

	@Column(name = "original_path", length = 128)
	private String originalPath;

	/** 공개 사본 폴더 `p/{uuid}`. 표시 키는 여기에 접미사를 붙인다. */
	@Column(name = "public_key", length = 128)
	private String publicKey;

	private Integer width;

	private Integer height;

	@Column(name = "byte_size")
	private Long byteSize;

	public static PostMedia image(long postId, int sortOrder, String uploadKey, String publicKey) {
		PostMedia media = new PostMedia();
		media.postId = postId;
		media.type = MediaType.IMAGE;
		media.status = PostMediaStatus.UPLOADING;
		media.sortOrder = sortOrder;
		media.uploadKey = uploadKey;
		media.publicKey = publicKey;
		return media;
	}

	/** 업로드 전에 앱이 알린 크기. 게시할 때 실제 업로드 크기와 맞춰 본다. */
	public void declare(int width, int height, long byteSize) {
		this.width = width;
		this.height = height;
		this.byteSize = byteSize;
	}

	public String displayKey() {
		return publicKey + DISPLAY_SUFFIX;
	}

	public String thumbKey() {
		return publicKey + THUMB_SUFFIX;
	}

	public void markProcessing() {
		this.status = PostMediaStatus.PROCESSING;
	}

	public void markReady(String originalPath, int width, int height, long byteSize) {
		this.status = PostMediaStatus.READY;
		this.originalPath = originalPath;
		this.width = width;
		this.height = height;
		this.byteSize = byteSize;
	}

	public void markFailed() {
		this.status = PostMediaStatus.FAILED;
	}

	/** 변환 전에 NAS 원본 위치를 남긴다. 처리 중에 숨겨져도 복원할 때 원본으로 다시 변환할 수 있다. */
	public void recordOriginal(String originalPath) {
		this.originalPath = originalPath;
	}

	/** 복원할 때 예전 공개 URL이 되살아나지 않게 새 폴더로 옮긴다. */
	public void movePublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public void clearOriginal() {
		this.originalPath = null;
	}
}
