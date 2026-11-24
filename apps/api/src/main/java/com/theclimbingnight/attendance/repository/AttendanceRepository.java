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

	/** 참석 엔티티를 영속성 컨텍스트에 올리지 않고 벙 ID만 얻는다. 벙을 먼저 잠근 뒤 참석을 잠금 조회하기 위해 쓴다. */
	@Query("select a.meetup.id from Attendance a where a.id = :id")
	Optional<Long> findMeetupIdById(@Param("id") long attendanceId);

	boolean existsByMeetupIdAndRosterMemberId(long meetupId, long rosterMemberId);

	List<Attendance> findAllByMeetupId(long meetupId);
}
