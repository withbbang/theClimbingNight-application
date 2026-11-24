package com.theclimbingnight.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.attendance.dto.AttendanceRateResponse;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceRateServiceTest {

	private static final Instant FROM = Instant.parse("2026-10-31T15:00:00Z");
	private static final Instant TO = Instant.parse("2026-11-30T15:00:00Z");

	@Mock AttendanceRepository attendanceRepository;
	@Mock MeetupRepository meetupRepository;
	@Mock RosterMemberRepository rosterMemberRepository;

	private AttendanceRateService service;
	private RosterMember roster;

	@BeforeEach
	void setUp() {
		service = new AttendanceRateService(attendanceRepository, meetupRepository, rosterMemberRepository,
				new MutableClock(Instant.parse("2026-11-23T15:30:00Z")));
		roster = RosterMember.create("me", null, RosterSource.IMPORT);
		ReflectionTestUtils.setField(roster, "id", 5L);
		given(rosterMemberRepository.findByUserId(7L)).willReturn(Optional.of(roster));
	}

	@Test
	void 비율은_소수_첫째_자리로_반올림한다() {
		// given
		given(meetupRepository.countHeld(FROM, TO)).willReturn(3L);
		given(attendanceRepository.countAttended(5L, FROM, TO)).willReturn(2L);

		// when
		AttendanceRateResponse response = service.mine(7L, AttendancePeriod.MONTH, LocalDate.of(2026, 11, 24));

		// then
		assertThat(response.rate()).isEqualByComparingTo(new BigDecimal("66.7"));
		assertThat(response.attendedCount()).isEqualTo(2);
		assertThat(response.meetupCount()).isEqualTo(3);
		assertThat(response.from()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(response.to()).isEqualTo(LocalDate.of(2026, 11, 30));
	}

	@Test
	void 열린_벙이_없으면_비율이_null이다() {
		// given
		given(meetupRepository.countHeld(FROM, TO)).willReturn(0L);

		// when
		AttendanceRateResponse response = service.mine(7L, AttendancePeriod.MONTH, LocalDate.of(2026, 11, 24));

		// then
		assertThat(response.rate()).isNull();
		assertThat(response.meetupCount()).isZero();
	}

	@Test
	void 명부가_없으면_참석_0회다() {
		// given
		given(rosterMemberRepository.findByUserId(8L)).willReturn(Optional.empty());
		given(meetupRepository.countHeld(FROM, TO)).willReturn(4L);

		// when
		AttendanceRateResponse response = service.mine(8L, AttendancePeriod.MONTH, LocalDate.of(2026, 11, 24));

		// then
		assertThat(response.attendedCount()).isZero();
		assertThat(response.meetupCount()).isEqualTo(4);
		assertThat(response.rate()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(attendanceRepository, never()).countAttended(anyLong(), any(), any());
	}

	@Test
	void 날짜가_없으면_서울_오늘이다() {
		// when
		AttendanceRateResponse response = service.mine(7L, AttendancePeriod.DAY, null);

		// then
		assertThat(response.from()).isEqualTo(LocalDate.of(2026, 11, 24));
		assertThat(response.to()).isEqualTo(LocalDate.of(2026, 11, 24));
	}

	@Test
	void 허용_날짜_바깥이면_400이다() {
		// when, then
		assertThatThrownBy(() -> service.mine(7L, AttendancePeriod.DAY, LocalDate.of(1999, 12, 31)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
		assertThatThrownBy(() -> service.mine(7L, AttendancePeriod.DAY, LocalDate.of(2101, 1, 1)))
				.isInstanceOf(BusinessException.class);
		assertThat(service.mine(7L, AttendancePeriod.DAY, LocalDate.of(2000, 1, 1)).from())
				.isEqualTo(LocalDate.of(2000, 1, 1));
		assertThat(service.mine(7L, AttendancePeriod.DAY, LocalDate.of(2100, 12, 31)).from())
				.isEqualTo(LocalDate.of(2100, 12, 31));
	}
}
