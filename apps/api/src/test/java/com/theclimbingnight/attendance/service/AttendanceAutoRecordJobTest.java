package com.theclimbingnight.attendance.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class AttendanceAutoRecordJobTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock MeetupRepository meetupRepository;
	@Mock AttendanceRecorder recorder;

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
	private Logger logger;

	@BeforeEach
	void attachAppender() {
		logger = (Logger) LoggerFactory.getLogger(AttendanceAutoRecordJob.class);
		logger.setLevel(Level.DEBUG);
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		logger.detachAppender(appender);
		appender.stop();
	}

	private long errorCount() {
		return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).count();
	}

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

	@Test
	void 같은_벙이_계속_실패하면_error는_한_번만_남긴다() {
		// given
		given(meetupRepository.findAttendanceDueIds(NOW)).willReturn(List.of(1L));
		given(recorder.record(1L)).willThrow(new IllegalStateException("boom"));
		AttendanceAutoRecordJob job = new AttendanceAutoRecordJob(meetupRepository, recorder, new MutableClock(NOW));

		// when
		job.run();
		job.run();

		// then
		assertThat(errorCount()).isEqualTo(1);
		assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.DEBUG).hasSize(1);
	}

	@Test
	void 성공했다가_다시_실패하면_error를_다시_남긴다() {
		// given
		given(meetupRepository.findAttendanceDueIds(NOW)).willReturn(List.of(1L));
		given(recorder.record(1L)).willThrow(new IllegalStateException("boom"))
				.willReturn(true)
				.willThrow(new IllegalStateException("boom"));
		AttendanceAutoRecordJob job = new AttendanceAutoRecordJob(meetupRepository, recorder, new MutableClock(NOW));

		// when
		job.run();
		job.run();
		job.run();

		// then
		assertThat(errorCount()).isEqualTo(2);
	}

	@Test
	void 대상에서_빠진_벙은_실패_기억에서_지운다() {
		// given
		given(meetupRepository.findAttendanceDueIds(NOW)).willReturn(List.of(1L), List.of(2L), List.of(1L));
		given(recorder.record(1L)).willThrow(new IllegalStateException("boom"));
		AttendanceAutoRecordJob job = new AttendanceAutoRecordJob(meetupRepository, recorder, new MutableClock(NOW));

		// when
		job.run();
		job.run();
		job.run();

		// then
		assertThat(errorCount()).isEqualTo(2);
	}
}
