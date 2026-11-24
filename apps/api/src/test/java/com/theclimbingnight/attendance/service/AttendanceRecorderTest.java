package com.theclimbingnight.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.attendance.entity.AttendanceSource;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceRecorderTest {

	private static final Instant STARTS = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant ENDS = Instant.parse("2026-01-01T13:00:00Z");

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock AttendanceRepository attendanceRepository;

	private MutableClock clock;
	private AttendanceRecorder recorder;
	private Meetup meetup;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(ENDS.plusSeconds(60));
		recorder = new AttendanceRecorder(meetupRepository, applicationRepository, attendanceRepository, clock);
		meetup = Meetup.create(new MeetupSpec("제목", null, "장소", STARTS, ENDS, 10, 24, true), null);
		ReflectionTestUtils.setField(meetup, "id", 10L);
		given(meetupRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meetup));
	}

	private MeetupApplication confirmed(long rosterId) {
		RosterMember roster = RosterMember.create("회원" + rosterId, null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", rosterId);
		return MeetupApplication.create(meetup, roster, true, STARTS.minusSeconds(100));
	}

	@Test
	void 끝난_벙의_확정자_전원을_참석으로_기록한다() {
		// given
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(10L,
				MeetupApplicationStatus.CONFIRMED)).willReturn(List.of(confirmed(1L), confirmed(2L)));

		// when
		boolean recorded = recorder.record(10L);

		// then
		assertThat(recorded).isTrue();
		ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
		verify(attendanceRepository, times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).allSatisfy(a -> {
			assertThat(a.getResult()).isEqualTo(AttendanceResult.ATTENDED);
			assertThat(a.getSource()).isEqualTo(AttendanceSource.AUTO);
			assertThat(a.getRecordedBy()).isNull();
		});
		assertThat(saved.getAllValues()).extracting(a -> a.getRosterMember().getId()).containsExactly(1L, 2L);
		assertThat(meetup.getAttendanceRecordedAt()).isEqualTo(clock.instant());
	}

	@Test
	void 이미_기록됐거나_취소된_벙은_건너뛴다() {
		// given
		meetup.markAttendanceRecorded(ENDS);

		// when
		boolean first = recorder.record(10L);
		meetup = Meetup.create(new MeetupSpec("제목", null, "장소", STARTS, ENDS, 10, 24, true), null);
		ReflectionTestUtils.setField(meetup, "id", 10L);
		meetup.cancel(STARTS.minusSeconds(5));
		given(meetupRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meetup));
		boolean second = recorder.record(10L);

		// then
		assertThat(first).isFalse();
		assertThat(second).isFalse();
		verify(attendanceRepository, never()).save(any(Attendance.class));
	}

	@Test
	void 종료_시각_정각이면_기록한다() {
		// given
		clock.advance(java.time.Duration.ofSeconds(-60));
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(10L,
				MeetupApplicationStatus.CONFIRMED)).willReturn(List.of(confirmed(1L)));

		// when, then
		assertThat(recorder.record(10L)).isTrue();
		verify(attendanceRepository).save(any(Attendance.class));
	}
}
