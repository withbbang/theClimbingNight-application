package com.theclimbingnight.penalty.entity;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "penalties")
public class Penalty extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "roster_member_id", nullable = false)
	private RosterMember rosterMember;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meetup_id", nullable = false)
	private Meetup meetup;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PenaltyType type;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "revoked_at")
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revoked_by")
	private User revokedBy;

	@Column(name = "revoke_reason", length = 200)
	private String revokeReason;

	public static Penalty create(RosterMember roster, Meetup meetup, PenaltyType type, User admin) {
		Penalty penalty = new Penalty();
		penalty.rosterMember = roster;
		penalty.meetup = meetup;
		penalty.type = type;
		penalty.createdBy = admin;
		return penalty;
	}

	public void revoke(User admin, String reason, Instant now) {
		if (!isActive()) {
			throw new IllegalStateException("이미 해제된 기록입니다");
		}
		this.revokedBy = admin;
		this.revokeReason = reason;
		this.revokedAt = now;
	}

	public boolean isActive() {
		return this.revokedAt == null;
	}
}
