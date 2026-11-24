package com.theclimbingnight.attendance.dto;

import com.theclimbingnight.attendance.service.AttendancePeriod;
import java.math.BigDecimal;
import java.time.LocalDate;

/** rate는 백분율(소수 첫째 자리). 참석 기록이 끝난 벙이 없으면 null. */
public record AttendanceRateResponse(AttendancePeriod period, LocalDate from, LocalDate to, long attendedCount,
		long meetupCount, BigDecimal rate) {
}
