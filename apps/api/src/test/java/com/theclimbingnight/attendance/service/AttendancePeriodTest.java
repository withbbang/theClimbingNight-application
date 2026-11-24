package com.theclimbingnight.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AttendancePeriodTest {

	@Test
	void 주는_월요일부터_일요일이다() {
		// given
		DateRange expected = new DateRange(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29));

		// when, then
		assertThat(AttendancePeriod.WEEK.rangeOf(LocalDate.of(2026, 11, 23))).isEqualTo(expected);
		assertThat(AttendancePeriod.WEEK.rangeOf(LocalDate.of(2026, 11, 29))).isEqualTo(expected);
		assertThat(AttendancePeriod.WEEK.rangeOf(LocalDate.of(2027, 1, 1)))
				.isEqualTo(new DateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)));
	}

	@Test
	void 월과_분기와_연의_첫날과_마지막날() {
		// when, then
		assertThat(AttendancePeriod.MONTH.rangeOf(LocalDate.of(2028, 2, 29)))
				.isEqualTo(new DateRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
		assertThat(AttendancePeriod.QUARTER.rangeOf(LocalDate.of(2026, 12, 31)))
				.isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
		assertThat(AttendancePeriod.YEAR.rangeOf(LocalDate.of(2026, 12, 31)))
				.isEqualTo(new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
		assertThat(AttendancePeriod.DAY.rangeOf(LocalDate.of(2026, 11, 24)))
				.isEqualTo(new DateRange(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24)));
	}

	@Test
	void 범위는_서울_자정_기준이다() {
		// given
		DateRange range = AttendancePeriod.DAY.rangeOf(LocalDate.of(2026, 11, 24));

		// when, then
		assertThat(range.start()).isEqualTo(Instant.parse("2026-11-23T15:00:00Z"));
		assertThat(range.endExclusive()).isEqualTo(Instant.parse("2026-11-24T15:00:00Z"));
	}
}
