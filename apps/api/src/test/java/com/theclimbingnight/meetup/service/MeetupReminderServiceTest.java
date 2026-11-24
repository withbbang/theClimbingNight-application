package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.user.entity.User;
import java.time.Duration;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetupReminderServiceTest {

	private static final Instant NOW = Instant.parse("2030-03-01T11:00:00Z");
	private static final Instant STARTS = NOW.plus(Duration.ofHours(14));
	private static final Instant UNTIL = NOW.plus(Duration.ofHours(15));
	private static final long MEETUP_ID = 10L;

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock ApplicationEventPublisher eventPublisher;
	private MeetupReminderService service;
	private Meetup meetup;

	@BeforeEach
	void setUp() {
		service = new MeetupReminderService(meetupRepository, applicationRepository, eventPublisher,
				new MutableClock(NOW));
		meetup = Meetup.create(spec(STARTS), User.create("host"));
		ReflectionTestUtils.setField(meetup, "id", MEETUP_ID);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.CONFIRMED)).willReturn(List.of(application(3L), application(4L)));
	}

	private static MeetupSpec spec(Instant startsAt) {
		return new MeetupSpec("정기벙", null, "더클라임", startsAt, startsAt.plusSeconds(7200), 10, 12, false);
	}

	private MeetupApplication application(long rosterId) {
		RosterMember roster = RosterMember.create("r" + rosterId, null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", rosterId);
		return MeetupApplication.create(meetup, roster, true, NOW);
	}

	@Test
	void 확정자만_대상이고_한_번만_보낸다() {
		// when
		boolean first = service.remind(MEETUP_ID, UNTIL);
		boolean second = service.remind(MEETUP_ID, UNTIL);

		// then
		assertThat(first).isTrue();
		assertThat(second).isFalse();
		assertThat(meetup.getReminderSentAt()).isEqualTo(NOW);
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(1)).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new MeetupReminderDueEvent(MEETUP_ID, List.of(3L, 4L)));
		verify(applicationRepository, never()).findAllByMeetupId(MEETUP_ID);
	}

	@Test
	void 확정자가_없으면_표시만_하고_이벤트는_없다() {
		// given
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.CONFIRMED)).willReturn(List.of());

		// when
		boolean result = service.remind(MEETUP_ID, UNTIL);

		// then
		assertThat(result).isTrue();
		assertThat(meetup.getReminderSentAt()).isEqualTo(NOW);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void 잠근_뒤_취소된_벙은_표시도_이벤트도_없다() {
		// given
		meetup.cancel(NOW);

		// when, then
		assertThat(service.remind(MEETUP_ID, UNTIL)).isFalse();
		assertThat(meetup.getReminderSentAt()).isNull();
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void 잠근_뒤_시작이_범위를_벗어나면_보내지_않는다() {
		// given: 후보를 읽은 뒤 날짜가 뒤로 밀린 벙, 이미 시작한 벙
		assertThat(service.remind(MEETUP_ID, STARTS)).isFalse();
		ReflectionTestUtils.setField(meetup, "startsAt", NOW);

		// when, then
		assertThat(service.remind(MEETUP_ID, UNTIL)).isFalse();
		assertThat(meetup.getReminderSentAt()).isNull();
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void 없는_벙은_건너뛴다() {
		// given
		given(meetupRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

		// when, then
		assertThat(service.remind(99L, UNTIL)).isFalse();
	}

	@Test
	void 시작_시각이_바뀌면_리마인드를_다시_보낸다() {
		// given
		service.remind(MEETUP_ID, UNTIL);
		Instant newStarts = STARTS.plus(Duration.ofMinutes(30));

		// when
		meetup.update(spec(newStarts));
		boolean again = service.remind(MEETUP_ID, UNTIL.plus(Duration.ofHours(1)));

		// then
		assertThat(again).isTrue();
		verify(eventPublisher, times(2)).publishEvent(any(Object.class));
	}

	@Test
	void 시작_시각이_같은_수정은_표시를_유지한다() {
		// given
		service.remind(MEETUP_ID, UNTIL);

		// when
		meetup.update(new MeetupSpec("제목만 바꿈", "설명", "다른 장소", STARTS, STARTS.plusSeconds(3600), 20, 6, true));

		// then
		assertThat(meetup.getReminderSentAt()).isEqualTo(NOW);
		assertThat(service.remind(MEETUP_ID, UNTIL)).isFalse();
	}
}
