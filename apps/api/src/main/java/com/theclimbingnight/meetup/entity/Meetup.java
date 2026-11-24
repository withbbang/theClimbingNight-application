package com.theclimbingnight.meetup.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import com.theclimbingnight.user.entity.User;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "meetups")
public class Meetup extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "place_name", length = 100)
	private String placeName;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "ends_at")
	private Instant endsAt;

	private Integer capacity;

	@Column(name = "late_cancel_hours", nullable = false)
	private int lateCancelHours;

	@Column(name = "participants_visible", nullable = false)
	private boolean participantsVisible;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "host_user_id")
	private User host;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MeetupStatus status;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "canceled_at")
	private Instant canceledAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MeetupSource source;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "attendance_recorded_at")
	private Instant attendanceRecordedAt;

	public static Meetup create(MeetupSpec spec, User host) {
		Meetup meetup = new Meetup();
		meetup.apply(spec);
		meetup.host = host;
		meetup.status = MeetupStatus.OPEN;
		meetup.source = MeetupSource.APP;
		return meetup;
	}

	public void update(MeetupSpec spec) {
		apply(spec);
	}

	public void cancel(Instant now) {
		this.status = MeetupStatus.CANCELED;
		this.canceledAt = now;
	}

	public boolean isOpen() {
		return this.status == MeetupStatus.OPEN;
	}

	public boolean hasStarted(Instant now) {
		return !now.isBefore(startsAt);
	}

	/** 종료 시각이 없으면 끝난 것으로 보지 않는다. */
	public boolean hasEnded(Instant now) {
		return endsAt != null && !now.isBefore(endsAt);
	}

	public Instant lateCancelDeadline() {
		return startsAt.minus(lateCancelHours, ChronoUnit.HOURS);
	}

	public void markAttendanceRecorded(Instant now) {
		this.attendanceRecordedAt = now;
	}

	public MeetupSpec toSpec() {
		return new MeetupSpec(title, description, placeName, startsAt, endsAt,
				capacity == null ? 0 : capacity, lateCancelHours, participantsVisible);
	}

	private void apply(MeetupSpec spec) {
		this.title = spec.title();
		this.description = spec.description();
		this.placeName = spec.placeName();
		this.startsAt = spec.startsAt();
		this.endsAt = spec.endsAt();
		this.capacity = spec.capacity();
		this.lateCancelHours = spec.lateCancelHours();
		this.participantsVisible = spec.participantsVisible();
	}
}
