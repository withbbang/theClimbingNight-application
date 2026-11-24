package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetupWithdrawalListenerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final long USER_ID = 7L;

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock RosterMemberRepository rosterMemberRepository;
	@Mock WaitlistPromoter promoter;

	private MeetupWithdrawalListener listener;

	@BeforeEach
	void setUp() {
		listener = new MeetupWithdrawalListener(meetupRepository, applicationRepository, rosterMemberRepository,
				promoter, new MutableClock(NOW));
	}

	@Test
	void 탈퇴하면_다가오는_벙_신청을_취소하고_대기자를_승격한다() {
		// given
		RosterMember roster = RosterMember.create("철수", null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", 3L);
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.of(roster));
		Meetup later = meetup(20L);
		Meetup sooner = meetup(10L);
		// 기준 시각 안쪽에서 확정된 신청이어도 탈퇴 취소는 늦은 취소가 아니다.
		MeetupApplication confirmed = MeetupApplication.create(later, roster, true, NOW.minusSeconds(3600));
		MeetupApplication waiting = MeetupApplication.create(sooner, roster, false, NOW.minusSeconds(3600));
		given(applicationRepository.findActiveUpcoming(3L, NOW)).willReturn(List.of(confirmed, waiting));
		given(meetupRepository.findByIdForUpdate(10L)).willReturn(Optional.of(sooner));
		given(meetupRepository.findByIdForUpdate(20L)).willReturn(Optional.of(later));

		// when
		listener.on(new UserWithdrawnEvent(USER_ID));

		// then
		assertThat(confirmed.getStatus()).isEqualTo(MeetupApplicationStatus.CANCELED);
		assertThat(confirmed.isLateCancel()).isFalse();
		assertThat(confirmed.getCancelReason()).isEqualTo("회원 탈퇴");
		assertThat(confirmed.getCanceledAt()).isEqualTo(NOW);
		assertThat(waiting.getStatus()).isEqualTo(MeetupApplicationStatus.CANCELED);
		assertThat(waiting.isLateCancel()).isFalse();
		verify(promoter).promote(later, NOW);
		verify(promoter, never()).promote(sooner, NOW);
		// 여러 벙을 잠글 때는 ID 순서로 잠가 동시 탈퇴끼리 교착하지 않게 한다.
		InOrder order = inOrder(meetupRepository);
		order.verify(meetupRepository).findByIdForUpdate(10L);
		order.verify(meetupRepository).findByIdForUpdate(20L);
	}

	@Test
	void 명부가_없으면_아무것도_하지_않는다() {
		// given
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

		// when
		listener.on(new UserWithdrawnEvent(USER_ID));

		// then
		verify(applicationRepository, never()).findActiveUpcoming(anyLong(), any());
		verify(meetupRepository, never()).findByIdForUpdate(anyLong());
		verify(promoter, never()).promote(any(), any());
	}

	private static Meetup meetup(long id) {
		Instant starts = NOW.plus(Duration.ofHours(2));
		Meetup meetup = Meetup.create(new MeetupSpec("제목", null, "장소", starts, starts.plus(Duration.ofHours(3)),
				5, 24, true), null);
		ReflectionTestUtils.setField(meetup, "id", id);
		return meetup;
	}
}
