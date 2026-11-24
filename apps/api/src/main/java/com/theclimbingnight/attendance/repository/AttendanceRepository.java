package com.theclimbingnight.attendance.repository;

import com.theclimbingnight.attendance.entity.Attendance;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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

	/** 참석 기록이 끝난(또는 이관된) 열린 벙 중 내가 참석한 수. 기간은 startsAt >= from, < to. */
	@Query("select count(a) from Attendance a where a.rosterMember.id = :rosterMemberId "
			+ "and a.result = com.theclimbingnight.attendance.entity.AttendanceResult.ATTENDED "
			+ "and a.meetup.status = com.theclimbingnight.meetup.entity.MeetupStatus.OPEN "
			+ "and a.meetup.startsAt >= :from and a.meetup.startsAt < :to "
			+ "and (a.meetup.attendanceRecordedAt is not null "
			+ "or a.meetup.source = com.theclimbingnight.meetup.entity.MeetupSource.IMPORT)")
	long countAttended(@Param("rosterMemberId") long rosterMemberId, @Param("from") Instant from,
			@Param("to") Instant to);
}
