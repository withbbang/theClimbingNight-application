package com.theclimbingnight.meetup.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetupReminderJobTest {

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupReminderService service;

	private static Instant kst(String dateTime) {
		return LocalDateTime.parse(dateTime).atZone(TimeZones.KST).toInstant();
	}

	private MeetupReminderJob jobAt(String kstDateTime) {
		return new MeetupReminderJob(meetupRepository, service, new MutableClock(kst(kstDateTime)));
	}

	@Test
	void 아홉시_전에는_보내지_않는다() {
		// when
		jobAt("2030-03-01T08:59:59").run();

		// then
		verifyNoInteractions(meetupRepository, service);
	}

	@Test
	void 아홉시_정각에는_실행한다() {
		// given
		Instant now = kst("2030-03-01T09:00:00");
		given(meetupRepository.findReminderDueIds(now, kst("2030-03-02T00:00:00"))).willReturn(List.of(1L));

		// when
		jobAt("2030-03-01T09:00:00").run();

		// then
		verify(service).remind(1L, kst("2030-03-02T00:00:00"));
	}

	@Test
	void 놓친_리마인드는_당일_아홉시부터_보낸다() {
		// given: 전날 20시에 서버가 꺼져 있었다. 당일 09시에 내일 00시까지 시작하는 벙을 찾는다.
		Instant now = kst("2030-03-01T09:00:00");
		given(meetupRepository.findReminderDueIds(now, kst("2030-03-02T00:00:00"))).willReturn(List.of(1L, 2L));

		// when
		jobAt("2030-03-01T09:00:00").run();

		// then
		verify(service).remind(1L, kst("2030-03-02T00:00:00"));
		verify(service).remind(2L, kst("2030-03-02T00:00:00"));
	}

	@Test
	void 스무시_직전까지는_오늘_벙만_본다() {
		// given
		Instant now = kst("2030-03-01T19:59:59");
		given(meetupRepository.findReminderDueIds(now, kst("2030-03-02T00:00:00"))).willReturn(List.of(1L));

		// when
		jobAt("2030-03-01T19:59:59").run();

		// then
		verify(service).remind(1L, kst("2030-03-02T00:00:00"));
	}

	@Test
	void 스무시부터_내일_벙까지_보낸다() {
		// given
		Instant now = kst("2030-03-01T20:00:00");
		given(meetupRepository.findReminderDueIds(now, kst("2030-03-03T00:00:00"))).willReturn(List.of(1L));

		// when
		jobAt("2030-03-01T20:00:00").run();

		// then
		verify(service).remind(1L, kst("2030-03-03T00:00:00"));
	}

	@Test
	void 달_경계도_한국_날짜로_자른다() {
		// given: 2월 28일 20시(윤년 아님) → 3월 2일 00시
		Instant now = kst("2030-02-28T20:00:00");
		given(meetupRepository.findReminderDueIds(now, kst("2030-03-02T00:00:00"))).willReturn(List.of(1L));

		// when
		jobAt("2030-02-28T20:00:00").run();

		// then
		verify(service).remind(1L, kst("2030-03-02T00:00:00"));
	}

	@Test
	void 한_벙이_실패해도_다음_벙을_처리한다() {
		// given
		Instant now = kst("2030-03-01T10:00:00");
		Instant until = kst("2030-03-02T00:00:00");
		given(meetupRepository.findReminderDueIds(now, until)).willReturn(List.of(1L, 2L));
		given(service.remind(1L, until)).willThrow(new IllegalStateException("boom"));

		// when
		jobAt("2030-03-01T10:00:00").run();

		// then
		verify(service).remind(2L, until);
	}

	@Test
	void 대상이_없으면_아무것도_하지_않는다() {
		// given
		given(meetupRepository.findReminderDueIds(any(), any())).willReturn(List.of());

		// when
		jobAt("2030-03-01T10:00:00").run();

		// then
		verify(service, never()).remind(anyLong(), any());
	}
}
