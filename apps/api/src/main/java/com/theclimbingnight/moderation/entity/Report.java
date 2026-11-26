package com.theclimbingnight.moderation.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reports", uniqueConstraints = @UniqueConstraint(name = "uk_reports_reporter_target",
		columnNames = {"reporter_id", "target_type", "target_id"}))
public class Report extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 16)
	private ReportTargetType targetType;

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private ReportReason reason;

	@Column(length = 500)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReportStatus status;

	@Column(name = "handled_by")
	private Long handledBy;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "handled_at")
	private Instant handledAt;

	public static Report open(long reporterId, ReportTargetType targetType, long targetId, ReportReason reason,
			String detail) {
		Report report = new Report();
		report.reporterId = reporterId;
		report.targetType = targetType;
		report.targetId = targetId;
		report.reason = reason;
		report.detail = detail;
		report.status = ReportStatus.OPEN;
		return report;
	}

	public void handle(ReportStatus result, long adminId, Instant now) {
		this.status = result;
		this.handledBy = adminId;
		this.handledAt = now;
	}
}
