package com.theclimbingnight.penalty.repository;

import com.theclimbingnight.penalty.dto.PenaltySummaryResponse;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

	/** 활성 기록을 잠그고 읽는다. 동시에 취소하는 쪽의 사유를 덮어쓰지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Penalty p where p.meetup.id = :meetupId and p.rosterMember.id = :rosterMemberId "
			+ "and p.type = :type and p.revokedAt is null")
	Optional<Penalty> findActiveForUpdate(@Param("meetupId") long meetupId,
			@Param("rosterMemberId") long rosterMemberId, @Param("type") PenaltyType type);

	List<Penalty> findAllByMeetupIdAndRevokedAtIsNull(long meetupId);

	/** 벙 제목·시각을 응답에 쓰므로 벙을 함께 읽는다. */
	@EntityGraph(attributePaths = "meetup")
	List<Penalty> findAllByRosterMemberIdOrderByIdDesc(long rosterMemberId);

	@Query("select new com.theclimbingnight.penalty.repository.PenaltyCount(p.rosterMember.id, count(p), "
			+ "sum(case when p.meetup.startsAt >= :recentFrom then 1 else 0 end)) "
			+ "from Penalty p where p.revokedAt is null and p.rosterMember.id in :ids "
			+ "group by p.rosterMember.id")
	List<PenaltyCount> countActiveByRosterIds(@Param("ids") Collection<Long> rosterIds,
			@Param("recentFrom") Instant recentFrom);

	/** 활성 기록이 있는 명부만, 최근 3개월 수·누적 수 내림차순, 명부 ID 오름차순. */
	@Query(value = "select new com.theclimbingnight.penalty.dto.PenaltySummaryResponse(r.id, r.somoimNickname, "
			+ "count(p), sum(case when m.startsAt >= :recentFrom then 1 else 0 end)) "
			+ "from Penalty p join p.rosterMember r join p.meetup m where p.revokedAt is null "
			+ "group by r.id, r.somoimNickname "
			+ "order by sum(case when m.startsAt >= :recentFrom then 1 else 0 end) desc, count(p) desc, r.id asc",
			countQuery = "select count(distinct p.rosterMember.id) from Penalty p where p.revokedAt is null")
	Page<PenaltySummaryResponse> summarize(@Param("recentFrom") Instant recentFrom, Pageable pageable);
}
