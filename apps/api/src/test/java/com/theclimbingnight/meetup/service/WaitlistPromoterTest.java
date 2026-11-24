package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import java.time.Instant;
import java.util.List;
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
class WaitlistPromoterTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-02-01T10:00:00Z");

	@Mock MeetupApplicationRepository applicationRepository;
	@Mock ApplicationEventPublisher eventPublisher;

	private WaitlistPromoter promoter;

	@BeforeEach
	void setUp() {
		promoter = new WaitlistPromoter(applicationRepository, new SeatCounter(applicationRepository), eventPublisher);
	}

	@Test
	void 빈_자리만큼_대기_순서대로_확정한다() {
		// given
		Meetup meetup = meetup(3);
		MeetupApplication w1 = waiting(meetup, 1L);
		MeetupApplication w2 = waiting(meetup, 2L);
		MeetupApplication w3 = waiting(meetup, 3L);
		given(applicationRepository.countByMeetupIdAndStatus(10L, MeetupApplicationStatus.CONFIRMED)).willReturn(1L);
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(10L,
				MeetupApplicationStatus.WAITLISTED)).willReturn(List.of(w1, w2, w3));

		// when
		List<MeetupApplication> promoted = promoter.promote(meetup, NOW);

		// then
		assertThat(promoted).containsExactly(w1, w2);
		assertThat(w1.getStatus()).isEqualTo(MeetupApplicationStatus.CONFIRMED);
		assertThat(w1.getConfirmedAt()).isEqualTo(NOW);
		assertThat(w2.getStatus()).isEqualTo(MeetupApplicationStatus.CONFIRMED);
		assertThat(w3.getStatus()).isEqualTo(MeetupApplicationStatus.WAITLISTED);
		ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(2)).publishEvent(events.capture());
		assertThat(events.getAllValues()).containsExactly(
				new MeetupApplicationPromotedEvent(10L, 1L), new MeetupApplicationPromotedEvent(10L, 2L));
	}

	@Test
	void 정원을_줄여_확정자가_넘치면_승격하지_않는다() {
		// given
		Meetup meetup = meetup(2);
		MeetupApplication w1 = waiting(meetup, 1L);
		given(applicationRepository.countByMeetupIdAndStatus(10L, MeetupApplicationStatus.CONFIRMED)).willReturn(3L);
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(10L,
				MeetupApplicationStatus.WAITLISTED)).willReturn(List.of(w1));

		// when
		List<MeetupApplication> promoted = promoter.promote(meetup, NOW);

		// then
		assertThat(promoted).isEmpty();
		assertThat(w1.getStatus()).isEqualTo(MeetupApplicationStatus.WAITLISTED);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	private Meetup meetup(int capacity) {
		Meetup meetup = Meetup.create(new MeetupSpec("제목", null, "장소", LATER, LATER.plusSeconds(7200), capacity, 24,
				true), null);
		ReflectionTestUtils.setField(meetup, "id", 10L);
		return meetup;
	}

	private MeetupApplication waiting(Meetup meetup, long rosterId) {
		RosterMember roster = RosterMember.create("회원" + rosterId, null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", rosterId);
		return MeetupApplication.create(meetup, roster, false, NOW);
	}
}
