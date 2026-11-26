package com.theclimbingnight.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_blocks")
@IdClass(UserBlock.Key.class)
public class UserBlock {

	@Id
	@Column(name = "blocker_id")
	private Long blockerId;

	@Id
	@Column(name = "blocked_id")
	private Long blockedId;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public static UserBlock of(long blockerId, long blockedId, Instant now) {
		UserBlock block = new UserBlock();
		block.blockerId = blockerId;
		block.blockedId = blockedId;
		block.createdAt = now;
		return block;
	}

	@Getter
	@NoArgsConstructor
	@EqualsAndHashCode
	public static class Key implements Serializable {
		private Long blockerId;
		private Long blockedId;
	}
}
