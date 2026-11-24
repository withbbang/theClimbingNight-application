package com.theclimbingnight.attendance.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.roster.entity.RosterMember;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "attendances", uniqueConstraints = @UniqueConstraint(
		name = "uk_attendances_meetup_roster", columnNames = {"meetup_id", "roster_member_id"}))
public class Attendance extends BaseTimeEntity {

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
	private AttendanceResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recorded_by")
	private User recordedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AttendanceSource source;

	public static Attendance auto(Meetup meetup, RosterMember roster) {
		Attendance attendance = new Attendance();
		attendance.meetup = meetup;
		attendance.rosterMember = roster;
		attendance.result = AttendanceResult.ATTENDED;
		attendance.source = AttendanceSource.AUTO;
		return attendance;
	}

	public void correct(AttendanceResult result, User admin) {
		this.result = result;
		this.recordedBy = admin;
		this.source = AttendanceSource.ADMIN;
	}
}
