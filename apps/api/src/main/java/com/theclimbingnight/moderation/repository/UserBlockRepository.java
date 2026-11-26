package com.theclimbingnight.moderation.repository;

import com.theclimbingnight.moderation.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

	/** 어느 쪽이 차단했든 두 사람 사이에 차단이 있으면 true. */
	@Query("""
			select count(b) > 0 from UserBlock b
			where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)
			""")
	boolean existsBetween(@Param("a") long a, @Param("b") long b);
}
