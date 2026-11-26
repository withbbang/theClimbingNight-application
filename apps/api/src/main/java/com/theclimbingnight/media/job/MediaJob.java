package com.theclimbingnight.media.job;

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
@Table(name = "media_jobs")
public class MediaJob extends BaseTimeEntity {

	static final int MAX_ERROR_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private MediaJobKind kind;

	@Column(nullable = false)
	private int priority;

	/** post_media id. */
	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MediaJobStatus status;

	@Column(nullable = false)
	private int attempts;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "next_run_at", nullable = false)
	private Instant nextRunAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "locked_at")
	private Instant lockedAt;

	@Column(name = "last_error", length = MAX_ERROR_LENGTH)
	private String lastError;

	public static MediaJob queue(MediaJobKind kind, long targetId, Instant now) {
		MediaJob job = new MediaJob();
		job.kind = kind;
		job.priority = kind.priority();
		job.targetId = targetId;
		job.status = MediaJobStatus.QUEUED;
		job.nextRunAt = now;
		return job;
	}

	public void markDone() {
		this.status = MediaJobStatus.DONE;
		this.lockedAt = null;
	}

	/** 실패를 기록하고 nextRunAt에 다시 시도하도록 되돌린다. */
	public void retryAt(Instant nextRunAt, String error) {
		this.attempts++;
		this.status = MediaJobStatus.QUEUED;
		this.nextRunAt = nextRunAt;
		this.lockedAt = null;
		this.lastError = truncate(error);
	}

	public void markFailed(String error) {
		this.attempts++;
		this.status = MediaJobStatus.FAILED;
		this.lockedAt = null;
		this.lastError = truncate(error);
	}

	private static String truncate(String error) {
		if (error == null || error.length() <= MAX_ERROR_LENGTH) {
			return error;
		}
		return error.substring(0, MAX_ERROR_LENGTH);
	}
}
