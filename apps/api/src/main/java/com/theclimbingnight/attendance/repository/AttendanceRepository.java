package com.theclimbingnight.attendance.repository;

import com.theclimbingnight.attendance.entity.Attendance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Attendance a where a.id = :id")
	Optional<Attendance> findByIdForUpdate(@Param("id") long id);

	boolean existsByMeetupIdAndRosterMemberId(long meetupId, long rosterMemberId);

	List<Attendance> findAllByMeetupId(long meetupId);
}
