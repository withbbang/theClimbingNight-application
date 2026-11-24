package com.theclimbingnight.attendance.service;

import com.theclimbingnight.attendance.dto.AttendanceRateResponse;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 참석률: 참석 기록이 끝난(또는 이관된) 열린 벙 중 내가 참석한 비율. */
@Service
@Transactional(readOnly = true)
public class AttendanceRateService {

	private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
	private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

	private final AttendanceRepository attendanceRepository;
	private final MeetupRepository meetupRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final Clock clock;

	public AttendanceRateService(AttendanceRepository attendanceRepository, MeetupRepository meetupRepository,
			RosterMemberRepository rosterMemberRepository, Clock clock) {
		this.attendanceRepository = attendanceRepository;
		this.meetupRepository = meetupRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.clock = clock;
	}

	public AttendanceRateResponse mine(long userId, AttendancePeriod period, LocalDate date) {
		LocalDate base = date != null ? date : clock.instant().atZone(TimeZones.KST).toLocalDate();
		if (base.isBefore(MIN_DATE) || base.isAfter(MAX_DATE)) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		DateRange range = period.rangeOf(base);
		Instant from = range.start();
		Instant to = range.endExclusive();
		long held = meetupRepository.countHeld(from, to);
		long attended = rosterMemberRepository.findByUserId(userId)
				.map(roster -> attendanceRepository.countAttended(roster.getId(), from, to)).orElse(0L);
		BigDecimal rate = held == 0 ? null
				: BigDecimal.valueOf(attended * 100).divide(BigDecimal.valueOf(held), 1, RoundingMode.HALF_UP);
		return new AttendanceRateResponse(period, range.from(), range.to(), attended, held, rate);
	}
}
