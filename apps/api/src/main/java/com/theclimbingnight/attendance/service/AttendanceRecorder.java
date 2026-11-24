package com.theclimbingnight.attendance.service;

import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 끝난 벙 하나의 확정자를 참석으로 기록한다. 벙 잠금 뒤에 상태를 다시 확인한다. */
@Service
public class AttendanceRecorder {

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final AttendanceRepository attendanceRepository;
	private final Clock clock;

	public AttendanceRecorder(MeetupRepository meetupRepository, MeetupApplicationRepository applicationRepository,
			AttendanceRepository attendanceRepository, Clock clock) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.attendanceRepository = attendanceRepository;
		this.clock = clock;
	}

	@Transactional
	public boolean record(long meetupId) {
		Meetup meetup = meetupRepository.findByIdForUpdate(meetupId).orElse(null);
		Instant now = clock.instant();
		if (meetup == null || !meetup.isOpen() || meetup.getAttendanceRecordedAt() != null
				|| !meetup.hasEnded(now)) {
			return false;
		}
		for (MeetupApplication application : applicationRepository
				.findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(meetupId, MeetupApplicationStatus.CONFIRMED)) {
			if (!attendanceRepository.existsByMeetupIdAndRosterMemberId(meetupId,
					application.getRosterMember().getId())) {
				attendanceRepository.save(Attendance.auto(meetup, application.getRosterMember()));
			}
		}
		meetup.markAttendanceRecorded(now);
		return true;
	}
}
