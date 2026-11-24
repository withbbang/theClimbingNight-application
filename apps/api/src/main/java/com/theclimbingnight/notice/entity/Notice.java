package com.theclimbingnight.notice.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import com.theclimbingnight.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "notices")
public class Notice extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(nullable = false)
	private boolean pinned;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	/** 제목은 앞뒤 공백을 지우고, 본문은 마크다운이라 그대로 둔다. */
	public static Notice create(String title, String body, boolean pinned, User author, Instant now) {
		Notice notice = new Notice();
		notice.title = title.strip();
		notice.body = body;
		notice.pinned = pinned;
		notice.author = author;
		notice.publishedAt = now;
		return notice;
	}

	/** 게시 시각과 작성자는 바꾸지 않는다. */
	public void update(String title, String body, boolean pinned) {
		this.title = title.strip();
		this.body = body;
		this.pinned = pinned;
	}
}
