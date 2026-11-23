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

	/** 아직 폐기되지 않은 토큰만 폐기한다. 반환값 0이면 다른 요청이 먼저 폐기한 것이다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now "
			+ "where t.id = :id and t.revokedAt is null")
	int revokeIfActive(@Param("id") long id, @Param("now") Instant now);
}
