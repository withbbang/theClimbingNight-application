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
}
