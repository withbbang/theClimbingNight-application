package com.theclimbingnight.penalty.repository;

import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Penalty p where p.id = :id")
	Optional<Penalty> findByIdForUpdate(@Param("id") long id);

	@Query("select p from Penalty p where p.meetup.id = :meetupId and p.rosterMember.id = :rosterMemberId "
			+ "and p.type = :type and p.revokedAt is null")
	Optional<Penalty> findActive(@Param("meetupId") long meetupId, @Param("rosterMemberId") long rosterMemberId,
			@Param("type") PenaltyType type);

	List<Penalty> findAllByMeetupIdAndRevokedAtIsNull(long meetupId);

	List<Penalty> findAllByRosterMemberIdOrderByIdDesc(long rosterMemberId);

	@Query("select new com.theclimbingnight.penalty.repository.PenaltyCount(p.rosterMember.id, count(p), "
			+ "sum(case when p.meetup.startsAt >= :recentFrom then 1 else 0 end)) "
			+ "from Penalty p where p.revokedAt is null and p.rosterMember.id in :ids "
			+ "group by p.rosterMember.id")
	List<PenaltyCount> countActiveByRosterIds(@Param("ids") Collection<Long> rosterIds,
			@Param("recentFrom") Instant recentFrom);
}
