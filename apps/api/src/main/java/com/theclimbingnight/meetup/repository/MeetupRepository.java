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
}
