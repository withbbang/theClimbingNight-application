package com.theclimbingnight.meetup.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import com.theclimbingnight.roster.entity.RosterMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "meetup_applications", uniqueConstraints = @UniqueConstraint(
		name = "uk_meetup_applications_meetup_roster", columnNames = {"meetup_id", "roster_member_id"}))
public class MeetupApplication extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meetup_id", nullable = false)
	private Meetup meetup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "roster_member_id", nullable = false)
	private RosterMember rosterMember;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MeetupApplicationStatus status;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "applied_at", nullable = false)
	private Instant appliedAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "canceled_at")
	private Instant canceledAt;

	@Column(name = "late_cancel", nullable = false)
	private boolean lateCancel;

	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;

	public static MeetupApplication create(Meetup meetup, RosterMember roster, boolean confirmed, Instant now) {
		MeetupApplication application = new MeetupApplication();
		application.meetup = meetup;
		application.rosterMember = roster;
		application.start(confirmed, now);
		return application;
	}

	/** 취소한 신청을 다시 넣는다. 신청 시각은 새로 잡혀 대기 순번 맨 뒤로 간다. */
	public void reapply(boolean confirmed, Instant now) {
		if (this.status != MeetupApplicationStatus.CANCELED) {
			throw new IllegalStateException("취소한 신청만 다시 신청할 수 있습니다: " + this.status);
		}
		start(confirmed, now);
		this.canceledAt = null;
		this.lateCancel = false;
		this.cancelReason = null;
	}

	public void confirm(Instant now) {
		if (this.status != MeetupApplicationStatus.WAITLISTED) {
			throw new IllegalStateException("대기 중인 신청만 확정할 수 있습니다: " + this.status);
		}
		this.status = MeetupApplicationStatus.CONFIRMED;
		this.confirmedAt = now;
	}

	public void cancel(boolean late, String reason, Instant now) {
		requireActive();
		this.status = MeetupApplicationStatus.CANCELED;
		this.lateCancel = late;
		this.cancelReason = reason;
		this.canceledAt = now;
	}

	public void remove(Instant now) {
		requireActive();
		this.status = MeetupApplicationStatus.REMOVED;
		this.lateCancel = false;
		this.canceledAt = now;
	}

	public boolean isActive() {
		return this.status == MeetupApplicationStatus.CONFIRMED || this.status == MeetupApplicationStatus.WAITLISTED;
	}

	private void start(boolean confirmed, Instant now) {
		this.status = confirmed ? MeetupApplicationStatus.CONFIRMED : MeetupApplicationStatus.WAITLISTED;
		this.appliedAt = now;
		this.confirmedAt = confirmed ? now : null;
	}

	private void requireActive() {
		if (!isActive()) {
			throw new IllegalStateException("확정 또는 대기 중인 신청이 아닙니다: " + this.status);
		}
	}
}
