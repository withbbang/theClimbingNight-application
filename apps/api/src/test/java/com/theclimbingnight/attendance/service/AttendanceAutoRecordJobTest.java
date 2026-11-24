package com.theclimbingnight.attendance.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceAutoRecordJobTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock MeetupRepository meetupRepository;
	@Mock AttendanceRecorder recorder;

	@Test
	void 한_벙이_실패해도_나머지를_기록한다() {
		// given
		given(meetupRepository.findAttendanceDueIds(NOW)).willReturn(List.of(1L, 2L));
		given(recorder.record(1L)).willThrow(new IllegalStateException("boom"));
		AttendanceAutoRecordJob job = new AttendanceAutoRecordJob(meetupRepository, recorder, new MutableClock(NOW));

		// when
		job.run();

		// then
		verify(recorder).record(1L);
		verify(recorder).record(2L);
	}
}
