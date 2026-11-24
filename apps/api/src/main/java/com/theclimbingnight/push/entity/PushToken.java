package com.theclimbingnight.push.entity;

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
@Table(name = "push_tokens")
public class PushToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private long userId;

	@Column(name = "expo_push_token", nullable = false, length = 255)
	private String expoPushToken;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PushPlatform platform;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	public static PushToken register(long userId, String expoPushToken, PushPlatform platform, Instant now) {
		PushToken token = new PushToken();
		token.userId = userId;
		token.expoPushToken = expoPushToken;
		token.platform = platform;
		token.lastSeenAt = now;
		return token;
	}

	/** 같은 토큰을 다른 사용자·플랫폼이 다시 등록한 경우 소유자를 옮긴다. */
	public void reassign(long userId, PushPlatform platform, Instant now) {
		this.userId = userId;
		this.platform = platform;
		this.lastSeenAt = now;
	}
}
