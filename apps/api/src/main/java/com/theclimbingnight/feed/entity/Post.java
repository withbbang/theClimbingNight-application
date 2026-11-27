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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "posts")
public class Post extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, length = 2200)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PostStatus status;

	@Column(name = "like_count", nullable = false)
	private int likeCount;

	@Column(name = "comment_count", nullable = false)
	private int commentCount;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "published_at")
	private Instant publishedAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "deleted_at")
	private Instant deletedAt;

	public static Post draft(long authorId) {
		Post post = new Post();
		post.authorId = authorId;
		post.body = "";
		post.status = PostStatus.DRAFT;
		return post;
	}

	/** DRAFT에서 게시한다. 사진 처리가 끝날 때까지 PENDING이다. */
	public void publish(String body) {
		this.body = body;
		this.status = PostStatus.PENDING;
	}

	/** 처음 보이게 될 때만 게시 시각을 정한다. 복원 뒤 다시 보여도 피드 위치는 그대로다. */
	public void markVisible(Instant now) {
		this.status = PostStatus.VISIBLE;
		if (this.publishedAt == null) {
			this.publishedAt = now;
		}
	}

	public void markFailed() {
		this.status = PostStatus.FAILED;
	}

	public void hide(boolean pending) {
		this.status = pending ? PostStatus.HIDDEN_PENDING : PostStatus.HIDDEN;
	}

	/** 숨김 해제. 사진을 다시 공개 버킷에 올릴 때까지 PENDING이다. */
	public void restore() {
		this.status = PostStatus.PENDING;
	}

	public void editBody(String body) {
		this.body = body;
	}

	public void softDelete(Instant now) {
		this.deletedAt = now;
	}

	public void addLike() {
		this.likeCount++;
	}

	/** 0 아래로 내려가지 않는다. */
	public void removeLike() {
		if (this.likeCount > 0) {
			this.likeCount--;
		}
	}

	/** 실제 행 수로 다시 센 값을 넣는다. */
	public void updateLikeCount(int count) {
		this.likeCount = count;
	}

	public void updateCommentCount(int count) {
		this.commentCount = count;
	}
}
