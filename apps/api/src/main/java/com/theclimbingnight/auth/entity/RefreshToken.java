package com.theclimbingnight.auth.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private long userId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
	private String tokenHash;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "revoked_at")
	private Instant revokedAt;

	public static RefreshToken issue(long userId, String tokenHash, Instant expiresAt) {
		RefreshToken token = new RefreshToken();
		token.userId = userId;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		return token;
	}

	public boolean isUsable(Instant now) {
		return revokedAt == null && now.isBefore(expiresAt);
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}
}
