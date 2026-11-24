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

	Optional<MeetupApplication> findByMeetupIdAndRosterMemberId(long meetupId, long rosterMemberId);

	long countByMeetupIdAndStatus(long meetupId, MeetupApplicationStatus status);

	/** 확정 순서. */
	List<MeetupApplication> findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(
			long meetupId, MeetupApplicationStatus status);

	/** 대기 순서. */
	List<MeetupApplication> findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(
			long meetupId, MeetupApplicationStatus status);

	List<MeetupApplication> findAllByMeetupId(long meetupId);

	List<MeetupApplication> findAllByMeetupIdInAndRosterMemberId(Collection<Long> meetupIds, long rosterMemberId);

	@Query("select new com.theclimbingnight.meetup.repository.MeetupStatusCount(a.meetup.id, a.status, count(a)) "
			+ "from MeetupApplication a where a.meetup.id in :ids "
			+ "and a.status in (com.theclimbingnight.meetup.entity.MeetupApplicationStatus.CONFIRMED, "
			+ "com.theclimbingnight.meetup.entity.MeetupApplicationStatus.WAITLISTED) "
			+ "group by a.meetup.id, a.status")
	List<MeetupStatusCount> countByMeetupIds(@Param("ids") Collection<Long> meetupIds);

	@Query("select a from MeetupApplication a join fetch a.meetup m where a.rosterMember.id = :rosterMemberId "
			+ "and a.status in (com.theclimbingnight.meetup.entity.MeetupApplicationStatus.CONFIRMED, "
			+ "com.theclimbingnight.meetup.entity.MeetupApplicationStatus.WAITLISTED) "
			+ "and m.status = com.theclimbingnight.meetup.entity.MeetupStatus.OPEN and m.startsAt > :now")
	List<MeetupApplication> findActiveUpcoming(@Param("rosterMemberId") long rosterMemberId,
			@Param("now") Instant now);
}
