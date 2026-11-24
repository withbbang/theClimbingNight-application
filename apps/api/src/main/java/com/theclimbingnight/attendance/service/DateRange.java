package com.theclimbingnight.attendance.service;

import com.theclimbingnight.common.TimeZones;
import java.time.Instant;
import java.time.LocalDate;

/** 서울 달력 날짜 범위. from·to 모두 포함한다. */
public record DateRange(LocalDate from, LocalDate to) {

	/** from 00:00 KST. */
	public Instant start() {
		return from.atStartOfDay(TimeZones.KST).toInstant();
	}

	/** to 다음 날 00:00 KST. 조회는 이 값 미만으로 건다. */
	public Instant endExclusive() {
		return to.plusDays(1).atStartOfDay(TimeZones.KST).toInstant();
	}
}
