package com.theclimbingnight.attendance.service;

import com.theclimbingnight.attendance.dto.AttendanceResponse;
import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

	private final AttendanceRepository attendanceRepository;
	private final PenaltyRepository penaltyRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public AttendanceService(AttendanceRepository attendanceRepository, PenaltyRepository penaltyRepository,
			UserRepository userRepository, Clock clock) {
		this.attendanceRepository = attendanceRepository;
		this.penaltyRepository = penaltyRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/** 참석 행만 잠근다(벙 잠금 → 참석/벌점 순서를 지키므로 벙 잠금은 필요 없다). 보정 기한은 없다. */
	@Transactional
	public AttendanceResponse correct(long attendanceId, long adminId, AttendanceResult result) {
		Attendance attendance = attendanceRepository.findByIdForUpdate(attendanceId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		AttendanceResult previous = attendance.getResult();
		if (previous == result) {
			return AttendanceResponse.from(attendance);
		}
		User admin = userRepository.getReferenceById(adminId);
		attendance.correct(result, admin);
		long meetupId = attendance.getMeetup().getId();
		long rosterId = attendance.getRosterMember().getId();
		Instant now = clock.instant();
		if (previous == AttendanceResult.NO_SHOW) {
			penaltyRepository.findActive(meetupId, rosterId, PenaltyType.NO_SHOW)
					.ifPresent(p -> p.revoke(admin, "참석 보정으로 취소", now));
		}
		if (result == AttendanceResult.NO_SHOW
				&& penaltyRepository.findActive(meetupId, rosterId, PenaltyType.NO_SHOW).isEmpty()) {
			penaltyRepository.save(Penalty.create(attendance.getRosterMember(), attendance.getMeetup(),
					PenaltyType.NO_SHOW, admin));
		}
		return AttendanceResponse.from(attendance);
	}
}
