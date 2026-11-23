package com.theclimbingnight.roster.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "roster_members", uniqueConstraints = @UniqueConstraint(
		name = "uk_roster_members_user", columnNames = "user_id"))
public class RosterMember extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "somoim_nickname", nullable = false, length = 50)
	private String somoimNickname;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(length = 255)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RosterSource source;

	public static RosterMember create(String somoimNickname, String note, RosterSource source) {
		RosterMember member = new RosterMember();
		member.somoimNickname = somoimNickname;
		member.note = note;
		member.source = source;
		return member;
	}

	public void linkTo(User user) {
		this.user = user;
	}

	public void unlink() {
		this.user = null;
	}
}
