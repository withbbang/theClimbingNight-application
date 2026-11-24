package com.theclimbingnight.attendance.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum AttendancePeriod {
	DAY, WEEK, MONTH, QUARTER, YEAR;

	/** date가 속한 기간. 주는 월~일. */
	public DateRange rangeOf(LocalDate date) {
		return switch (this) {
			case DAY -> new DateRange(date, date);
			case WEEK -> {
				LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				yield new DateRange(monday, monday.plusDays(6));
			}
			case MONTH -> new DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
			case QUARTER -> {
				LocalDate first = date.withMonth((date.getMonthValue() - 1) / 3 * 3 + 1).withDayOfMonth(1);
				yield new DateRange(first, first.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()));
			}
			case YEAR -> new DateRange(date.with(TemporalAdjusters.firstDayOfYear()),
					date.with(TemporalAdjusters.lastDayOfYear()));
		};
	}
}
