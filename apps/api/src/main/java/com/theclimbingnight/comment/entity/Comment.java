package com.theclimbingnight.comment.entity;

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
@Table(name = "comments")
public class Comment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "reply_to_user_id")
	private Long replyToUserId;

	@Column(nullable = false, length = 500)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private CommentStatus status;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "deleted_at")
	private Instant deletedAt;

	public static Comment create(long postId, long authorId, Long parentId, Long replyToUserId, String body) {
		Comment comment = new Comment();
		comment.postId = postId;
		comment.authorId = authorId;
		comment.parentId = parentId;
		comment.replyToUserId = replyToUserId;
		comment.body = body;
		comment.status = CommentStatus.VISIBLE;
		return comment;
	}

	public void hide(boolean pending) {
		this.status = pending ? CommentStatus.HIDDEN_PENDING : CommentStatus.HIDDEN;
	}

	public void restore() {
		this.status = CommentStatus.VISIBLE;
	}

	public void softDelete(Instant now) {
		this.deletedAt = now;
	}

	/** 지워지지 않았고 숨겨지지 않았다. */
	public boolean isShown() {
		return deletedAt == null && status == CommentStatus.VISIBLE;
	}

	public boolean isHidden() {
		return status == CommentStatus.HIDDEN_PENDING || status == CommentStatus.HIDDEN;
	}
}
