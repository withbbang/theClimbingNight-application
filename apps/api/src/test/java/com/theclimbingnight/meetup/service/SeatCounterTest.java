package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeatCounterTest {

	@Mock MeetupApplicationRepository applicationRepository;

	private Meetup meetup(Integer capacity) {
		Instant starts = Instant.parse("2026-02-01T10:00:00Z");
		Meetup meetup = Meetup.create(
				new MeetupSpec("제목", null, "장소", starts, starts.plusSeconds(10800), capacity == null ? 1 : capacity, 24, true), null);
		ReflectionTestUtils.setField(meetup, "id", 10L);
		ReflectionTestUtils.setField(meetup, "capacity", capacity);
		return meetup;
	}

	@Test
	void 정원에서_확정자를_뺀_빈_자리를_돌려준다() {
		// given
		given(applicationRepository.countByMeetupIdAndStatus(10L, MeetupApplicationStatus.CONFIRMED)).willReturn(3L);

		// when, then
		assertThat(new SeatCounter(applicationRepository).freeSeats(meetup(5))).isEqualTo(2L);
	}

	@Test
	void 확정자가_정원을_넘어도_음수가_아니다() {
		// given
		given(applicationRepository.countByMeetupIdAndStatus(10L, MeetupApplicationStatus.CONFIRMED)).willReturn(7L);

		// when, then
		assertThat(new SeatCounter(applicationRepository).freeSeats(meetup(5))).isZero();
	}

	@Test
	void 정원이_없으면_제한이_없다() {
		// when, then
		assertThat(new SeatCounter(applicationRepository).freeSeats(meetup(null))).isEqualTo(Long.MAX_VALUE);
	}
}
