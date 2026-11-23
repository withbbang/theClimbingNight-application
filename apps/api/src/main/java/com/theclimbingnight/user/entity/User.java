package com.theclimbingnight.user.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 16, unique = true)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private UserStatus status;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	public static User create(String nickname) {
		User user = new User();
		user.nickname = nickname;
		user.role = Role.USER;
		user.status = UserStatus.ACTIVE;
		return user;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public void withdraw(Instant now) {
		this.status = UserStatus.WITHDRAWN;
		this.withdrawnAt = now;
		this.nickname = null;
	}

	public boolean isActive() {
		return this.status == UserStatus.ACTIVE;
	}
}
