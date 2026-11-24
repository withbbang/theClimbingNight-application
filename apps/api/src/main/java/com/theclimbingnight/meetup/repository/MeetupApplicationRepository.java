package com.theclimbingnight.meetup.repository;

import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupApplicationRepository extends JpaRepository<MeetupApplication, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from MeetupApplication a where a.id = :id")
	Optional<MeetupApplication> findByIdForUpdate(@Param("id") long id);

	/** 신청 엔티티를 영속성 컨텍스트에 올리지 않고 벙 ID만 얻는다. 벙을 먼저 잠근 뒤 신청을 잠금 조회하기 위해 쓴다. */
	@Query("select a.meetup.id from MeetupApplication a where a.id = :id")
	Optional<Long> findMeetupIdById(@Param("id") long applicationId);

	Optional<MeetupApplication> findByMeetupIdAndRosterMemberId(long meetupId, long rosterMemberId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from MeetupApplication a where a.meetup.id = :meetupId and a.rosterMember.id = :rosterMemberId")
	Optional<MeetupApplication> findByMeetupIdAndRosterMemberIdForUpdate(@Param("meetupId") long meetupId,
			@Param("rosterMemberId") long rosterMemberId);

	long countByMeetupIdAndStatus(long meetupId, MeetupApplicationStatus status);

	/** 확정 순서. */
	List<MeetupApplication> findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(
			long meetupId, MeetupApplicationStatus status);

	/** 대기 순서. */
	List<MeetupApplication> findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(
			long meetupId, MeetupApplicationStatus status);

	/** 명단 표시용: 명부(닉네임)를 함께 읽어 N+1을 피한다. 확정 순서. */
	@Query("select a from MeetupApplication a join fetch a.rosterMember "
			+ "where a.meetup.id = :meetupId and a.status = :status order by a.confirmedAt asc, a.id asc")
	List<MeetupApplication> findAllWithRosterByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(
			@Param("meetupId") long meetupId, @Param("status") MeetupApplicationStatus status);

	/** 명단 표시용: 명부(닉네임)를 함께 읽어 N+1을 피한다. 대기 순서. */
	@Query("select a from MeetupApplication a join fetch a.rosterMember "
			+ "where a.meetup.id = :meetupId and a.status = :status order by a.appliedAt asc, a.id asc")
	List<MeetupApplication> findAllWithRosterByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(
			@Param("meetupId") long meetupId, @Param("status") MeetupApplicationStatus status);

	List<MeetupApplication> findAllByMeetupId(long meetupId);

	List<MeetupApplication> findAllByMeetupIdInAndRosterMemberId(Collection<Long> meetupIds, long rosterMemberId);

	@Query("select new com.theclimbingnight.meetup.repository.MeetupStatusCount(a.meetup.id, a.status, count(a)) "
			+ "from MeetupApplication a where a.meetup.id in :ids "
			+ "and a.status in (com.theclimbingnight.meetup.entity.MeetupApplicationStatus.CONFIRMED, "
			+ "com.theclimbingnight.meetup.entity.MeetupApplicationStatus.WAITLISTED) "
			+ "group by a.meetup.id, a.status")
	List<MeetupStatusCount> countByMeetupIds(@Param("ids") Collection<Long> meetupIds);

	/**
	 * 명부의 확정·대기 신청 중 OPEN이고 아직 시작하지 않은 벙의 ID만 오름차순으로 준다. 엔티티를 영속성 컨텍스트에 올리지 않으므로,
	 * 뒤이어 잠금 조회한 벙·신청이 낡은 상태로 남지 않는다 (Hibernate는 이미 관리 중인 엔티티를 잠금 조회로 새로 고치지 않는다).
	 */
	@Query("select m.id from MeetupApplication a join a.meetup m where a.rosterMember.id = :rosterMemberId "
			+ "and a.status in (com.theclimbingnight.meetup.entity.MeetupApplicationStatus.CONFIRMED, "
			+ "com.theclimbingnight.meetup.entity.MeetupApplicationStatus.WAITLISTED) "
			+ "and m.status = com.theclimbingnight.meetup.entity.MeetupStatus.OPEN and m.startsAt > :now "
			+ "order by m.id")
	List<Long> findActiveUpcomingMeetupIds(@Param("rosterMemberId") long rosterMemberId, @Param("now") Instant now);
}
