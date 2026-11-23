package com.theclimbingnight.membership.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
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
@Table(name = "membership_requests")
public class MembershipRequest extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "somoim_nickname", nullable = false, length = 50)
	private String somoimNickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MembershipRequestStatus status;

	@Column(name = "reject_reason", length = 200)
	private String rejectReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "roster_member_id")
	private RosterMember rosterMember;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	public static MembershipRequest create(User user, String somoimNickname) {
		MembershipRequest request = new MembershipRequest();
		request.user = user;
		request.somoimNickname = somoimNickname;
		request.status = MembershipRequestStatus.PENDING;
		return request;
	}

	public boolean isPending() {
		return this.status == MembershipRequestStatus.PENDING;
	}

	public void approve(RosterMember rosterMember, User reviewer, Instant now) {
		requirePending();
		this.status = MembershipRequestStatus.APPROVED;
		this.rosterMember = rosterMember;
		this.reviewedBy = reviewer;
		this.reviewedAt = now;
	}

	/** reviewer가 null이면 시스템(탈퇴 자동 종료)이 거절한 것이다. */
	public void reject(String reason, User reviewer, Instant now) {
		requirePending();
		this.status = MembershipRequestStatus.REJECTED;
		this.rejectReason = reason;
		this.reviewedBy = reviewer;
		this.reviewedAt = now;
	}

	private void requirePending() {
		if (!isPending()) {
			throw new IllegalStateException("처리 대기 중인 신청이 아닙니다: " + this.status);
		}
	}
}
