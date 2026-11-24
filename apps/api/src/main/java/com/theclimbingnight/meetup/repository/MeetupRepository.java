package com.theclimbingnight.meetup.repository;

import com.theclimbingnight.meetup.entity.Meetup;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Meetup m where m.id = :id")
	Optional<Meetup> findByIdForUpdate(@Param("id") long id);

	List<Meetup> findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(Instant from, Instant to);

	@Query("select m.id from Meetup m where m.status = com.theclimbingnight.meetup.entity.MeetupStatus.OPEN "
			+ "and m.source = com.theclimbingnight.meetup.entity.MeetupSource.APP "
			+ "and m.attendanceRecordedAt is null and m.endsAt <= :now order by m.endsAt")
	List<Long> findAttendanceDueIds(@Param("now") Instant now);

	/** 참석 기록이 끝난(또는 이관된) 열린 벙 수. 기간은 startsAt >= from, < to. */
	@Query("select count(m) from Meetup m where m.status = com.theclimbingnight.meetup.entity.MeetupStatus.OPEN "
			+ "and m.startsAt >= :from and m.startsAt < :to "
			+ "and (m.attendanceRecordedAt is not null "
			+ "or m.source = com.theclimbingnight.meetup.entity.MeetupSource.IMPORT)")
	long countHeld(@Param("from") Instant from, @Param("to") Instant to);
}
