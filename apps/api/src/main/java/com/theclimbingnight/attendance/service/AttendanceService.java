package com.theclimbingnight.attendance.service;

import com.theclimbingnight.attendance.dto.AttendanceResponse;
import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.repository.MeetupRepository;
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
	private final MeetupRepository meetupRepository;
	private final PenaltyRepository penaltyRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public AttendanceService(AttendanceRepository attendanceRepository, MeetupRepository meetupRepository,
			PenaltyRepository penaltyRepository,
			UserRepository userRepository, Clock clock) {
		this.attendanceRepository = attendanceRepository;
		this.meetupRepository = meetupRepository;
		this.penaltyRepository = penaltyRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * 잠금 순서는 사용자 → 벙 → 참석 → 블랙리스트다. 운영진 행을 먼저 잠그고(참석·블랙리스트의 FK 부모라 나중에 공유 잠금이 걸리면
	 * 벙 → 사용자 순서가 된다), 참석의 벙 ID만 읽어 벙과 참석을 차례로 잠근다
	 * (블랙리스트 INSERT가 FK 부모인 벙에 공유 잠금을 걸어 참석 → 벙 순서가 되는 것을 막는다).
	 * 활성 노쇼 기록은 잠그고 읽어, 동시에 취소한 다른 운영진의 사유를 덮어쓰지 않는다. 보정 기한은 없다.
	 */
	@Transactional
	public AttendanceResponse correct(long attendanceId, long adminId, AttendanceResult result) {
		User admin = userRepository.findByIdForUpdate(adminId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		long meetupId = attendanceRepository.findMeetupIdById(attendanceId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		meetupRepository.findByIdForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		Attendance attendance = attendanceRepository.findByIdForUpdate(attendanceId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		AttendanceResult previous = attendance.getResult();
		if (previous == result) {
			return AttendanceResponse.from(attendance);
		}
		attendance.correct(result, admin);
		long rosterId = attendance.getRosterMember().getId();
		Instant now = clock.instant();
		if (previous == AttendanceResult.NO_SHOW) {
			penaltyRepository.findActiveForUpdate(meetupId, rosterId, PenaltyType.NO_SHOW)
					.ifPresent(p -> p.revoke(admin, "참석 보정으로 취소", now));
		}
		if (result == AttendanceResult.NO_SHOW
				&& penaltyRepository.findActiveForUpdate(meetupId, rosterId, PenaltyType.NO_SHOW).isEmpty()) {
			penaltyRepository.save(Penalty.create(attendance.getRosterMember(), attendance.getMeetup(),
					PenaltyType.NO_SHOW, admin));
		}
		return AttendanceResponse.from(attendance);
	}
}
