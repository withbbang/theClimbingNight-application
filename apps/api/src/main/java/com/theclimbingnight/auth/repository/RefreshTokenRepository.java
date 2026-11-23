package com.theclimbingnight.auth.repository;

import com.theclimbingnight.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now "
			+ "where t.userId = :userId and t.revokedAt is null")
	int revokeAllActiveByUserId(@Param("userId") long userId, @Param("now") Instant now);
}
