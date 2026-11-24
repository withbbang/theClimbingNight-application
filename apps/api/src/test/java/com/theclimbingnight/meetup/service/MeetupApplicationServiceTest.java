package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.dto.MyApplicationResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
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
class MeetupApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant STARTS = NOW.plus(Duration.ofHours(48));
	private static final Instant DEADLINE = NOW.plus(Duration.ofHours(24));
	private static final long MEETUP_ID = 10L;
	private static final long USER_ID = 7L;
	private static final long ROSTER_ID = 3L;

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock RosterMemberRepository rosterMemberRepository;
	@Mock WaitlistPromoter promoter;
	@Mock UserRepository userRepository;

	private MutableClock clock;
	private MeetupApplicationService service;
	private RosterMember roster;
	private Meetup meetup;
	private MeetupApplication lastSaved;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW);
		service = new MeetupApplicationService(meetupRepository, applicationRepository, rosterMemberRepository,
				userRepository, promoter, clock);
		roster = roster(ROSTER_ID);
		meetup = meetup(2);
		given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user(Role.MEMBER)));
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.of(roster));
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));
		given(applicationRepository.findByMeetupIdAndRosterMemberId(MEETUP_ID, ROSTER_ID)).willReturn(Optional.empty());
		given(applicationRepository.save(any(MeetupApplication.class))).willAnswer(inv -> {
			MeetupApplication saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);
			lastSaved = saved;
			return saved;
		});
	}

	@Test
	void 정원_안이면_확정이다() {
		// given
		given(applicationRepository.countByMeetupIdAndStatus(MEETUP_ID, MeetupApplicationStatus.CONFIRMED))
				.willReturn(1L);

		// when
		MyApplicationResponse response = service.apply(MEETUP_ID, USER_ID);

		// then
		assertThat(response.applicationId()).isEqualTo(100L);
		assertThat(response.status()).isEqualTo(MeetupApplicationStatus.CONFIRMED);
		assertThat(response.waitlistPosition()).isNull();
		assertThat(response.lateCancel()).isFalse();
		verify(applicationRepository).save(any(MeetupApplication.class));
	}

	@Test
	void 정원이_차면_대기이고_순번이_나온다() {
		// given
		given(applicationRepository.countByMeetupIdAndStatus(MEETUP_ID, MeetupApplicationStatus.CONFIRMED))
				.willReturn(2L);
		MeetupApplication ahead = MeetupApplication.create(meetup, roster(1L), false, NOW.minusSeconds(60));
		given(applicationRepository.findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.WAITLISTED)).willAnswer(inv -> List.of(ahead, lastSaved));

		// when
		MyApplicationResponse response = service.apply(MEETUP_ID, USER_ID);

		// then
		assertThat(response.status()).isEqualTo(MeetupApplicationStatus.WAITLISTED);
		assertThat(response.waitlistPosition()).isEqualTo(2);
	}

	private static User user(Role role) {
		User user = User.create("회원");
		user.changeRole(role);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	@Test
	void 사용자_잠금이_벙_잠금보다_먼저다() {
		// when
		service.apply(MEETUP_ID, USER_ID);

		// then
		InOrder order = inOrder(userRepository, meetupRepository);
		order.verify(userRepository).findByIdForUpdate(USER_ID);
		order.verify(meetupRepository).findByIdForUpdate(MEETUP_ID);
	}

	@Test
	void 자격이_없거나_비활성이면_403이고_벙을_잠그지_않는다() {
		// given
		User plain = user(Role.USER);
		User withdrawn = user(Role.MEMBER);
		withdrawn.withdraw(NOW);
		for (User blocked : List.of(plain, withdrawn)) {
			given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(blocked));

			// when & then
			assertThatThrownBy(() -> service.apply(MEETUP_ID, USER_ID)).isInstanceOf(BusinessException.class)
					.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN);
		}
		verify(meetupRepository, never()).findByIdForUpdate(anyLong());
		verify(applicationRepository, never()).save(any());
	}

	@Test
	void 벙_잠금이_확정자_수_세기보다_먼저다() {
		// when
		service.apply(MEETUP_ID, USER_ID);

		// then
		InOrder order = inOrder(rosterMemberRepository, meetupRepository, applicationRepository);
		order.verify(rosterMemberRepository).findByUserId(USER_ID);
		order.verify(meetupRepository).findByIdForUpdate(MEETUP_ID);
		order.verify(applicationRepository).countByMeetupIdAndStatus(MEETUP_ID, MeetupApplicationStatus.CONFIRMED);
	}

	@Test
	void 명부가_없으면_403이다() {
		// given
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.apply(MEETUP_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_ROSTER_REQUIRED);
		assertThatThrownBy(() -> service.cancel(MEETUP_ID, USER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_ROSTER_REQUIRED);
		verify(meetupRepository, never()).findByIdForUpdate(anyLong());
	}

	@Test
	void 시작_시각_정각에는_신청과_취소가_막힌다() {
		// given
		setNow(STARTS);
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);

		// when, then
		given(applicationRepository.findByMeetupIdAndRosterMemberId(MEETUP_ID, ROSTER_ID)).willReturn(Optional.empty());
		given(applicationRepository.findByMeetupIdAndRosterMemberIdForUpdate(MEETUP_ID, ROSTER_ID))
				.willReturn(Optional.empty());
		assertThatThrownBy(() -> service.apply(MEETUP_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_ALREADY_STARTED);
		given(applicationRepository.findByMeetupIdAndRosterMemberIdForUpdate(MEETUP_ID, ROSTER_ID))
				.willReturn(Optional.of(mine));
		assertThatThrownBy(() -> service.cancel(MEETUP_ID, USER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_ALREADY_STARTED);
		assertThat(mine.getStatus()).isEqualTo(MeetupApplicationStatus.CONFIRMED);
	}

	@Test
	void 취소했던_벙은_다시_신청하면_새_순서로_들어간다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW.minusSeconds(3600));
		mine.cancel(true, "사정", NOW.minusSeconds(60));
		given(applicationRepository.countByMeetupIdAndStatus(MEETUP_ID, MeetupApplicationStatus.CONFIRMED))
				.willReturn(0L);

		// when
		MyApplicationResponse response = service.apply(MEETUP_ID, USER_ID);

		// then
		assertThat(response.applicationId()).isEqualTo(50L);
		assertThat(mine.getStatus()).isEqualTo(MeetupApplicationStatus.CONFIRMED);
		assertThat(mine.getAppliedAt()).isEqualTo(NOW);
		assertThat(mine.isLateCancel()).isTrue();
		assertThat(mine.getCancelReason()).isNull();
		verify(applicationRepository, never()).save(any(MeetupApplication.class));
	}

	@Test
	void 늦은_취소_표시는_재신청_뒤_정상_취소해도_남는다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		setNow(DEADLINE.plusSeconds(3600));
		service.cancel(MEETUP_ID, USER_ID, null);
		assertThat(mine.isLateCancel()).isTrue();
		given(applicationRepository.countByMeetupIdAndStatus(MEETUP_ID, MeetupApplicationStatus.CONFIRMED))
				.willReturn(0L);
		service.apply(MEETUP_ID, USER_ID);
		assertThat(mine.getConfirmedAt()).isEqualTo(DEADLINE.plusSeconds(3600));

		// when
		setNow(DEADLINE.plusSeconds(7200));
		MyApplicationResponse response = service.cancel(MEETUP_ID, USER_ID, null);

		// then
		assertThat(response.lateCancel()).isTrue();
		assertThat(mine.isLateCancel()).isTrue();
	}

	@Test
	void 취소된_벙에서의_본인_취소는_기준_시각_뒤여도_늦은_취소가_아니다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		meetup.cancel(NOW);
		setNow(DEADLINE.plusSeconds(1));

		// when
		MyApplicationResponse response = service.cancel(MEETUP_ID, USER_ID, null);

		// then
		assertThat(response.lateCancel()).isFalse();
		assertThat(mine.isLateCancel()).isFalse();
	}

	@Test
	void 이미_신청했으면_409이다() {
		// given
		existing(MeetupApplicationStatus.WAITLISTED, NOW);

		// when, then
		assertThatThrownBy(() -> service.apply(MEETUP_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_ALREADY_APPLIED);
	}

	@Test
	void 취소된_벙에는_신청할_수_없다() {
		// given
		meetup.cancel(NOW);

		// when, then
		assertThatThrownBy(() -> service.apply(MEETUP_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_CANCELED);
	}

	@Test
	void 내보내진_벙은_다시_신청할_수_없다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		mine.remove(NOW);

		// when, then
		assertThatThrownBy(() -> service.apply(MEETUP_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_APPLICATION_REMOVED);
		assertThat(mine.getStatus()).isEqualTo(MeetupApplicationStatus.REMOVED);
	}

	@Test
	void 기준_시각_정각의_취소는_늦은_취소다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		setNow(DEADLINE);

		// when
		MyApplicationResponse response = service.cancel(MEETUP_ID, USER_ID, "  몸이 아파요  ");

		// then
		assertThat(response.status()).isEqualTo(MeetupApplicationStatus.CANCELED);
		assertThat(response.lateCancel()).isTrue();
		assertThat(response.cancelReason()).isEqualTo("몸이 아파요");
		assertThat(mine.getCanceledAt()).isEqualTo(DEADLINE);
	}

	@Test
	void 기준_시각_1초_전의_취소는_늦은_취소가_아니다() {
		// given
		existing(MeetupApplicationStatus.CONFIRMED, NOW);
		setNow(DEADLINE.minusSeconds(1));

		// when
		MyApplicationResponse response = service.cancel(MEETUP_ID, USER_ID, "   ");

		// then
		assertThat(response.lateCancel()).isFalse();
		assertThat(response.cancelReason()).isNull();
	}

	@Test
	void 기준_시각_이후_자동_확정된_사람의_취소는_늦은_취소가_아니다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.WAITLISTED, NOW);
		mine.confirm(DEADLINE.plus(Duration.ofHours(1)));
		setNow(DEADLINE.plus(Duration.ofHours(2)));

		// when
		MyApplicationResponse response = service.cancel(MEETUP_ID, USER_ID, null);

		// then
		assertThat(response.lateCancel()).isFalse();
		verify(promoter).promote(meetup, DEADLINE.plus(Duration.ofHours(2)));
	}

	@Test
	void 대기자의_취소는_늦은_취소가_아니고_승격도_없다() {
		// given
		existing(MeetupApplicationStatus.WAITLISTED, NOW);
		setNow(DEADLINE.plusSeconds(1));

		// when
		MyApplicationResponse response = service.cancel(MEETUP_ID, USER_ID, null);

		// then
		assertThat(response.status()).isEqualTo(MeetupApplicationStatus.CANCELED);
		assertThat(response.lateCancel()).isFalse();
		verify(promoter, never()).promote(any(), any());
	}

	@Test
	void 확정자가_취소하면_대기_1번이_확정된다() {
		// given
		existing(MeetupApplicationStatus.CONFIRMED, NOW);

		// when
		service.cancel(MEETUP_ID, USER_ID, null);

		// then
		verify(promoter).promote(meetup, NOW);
	}

	@Test
	void 내_신청이_없으면_404이고_비활성이면_409이다() {
		// when, then
		assertThatThrownBy(() -> service.cancel(MEETUP_ID, USER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.COMMON_NOT_FOUND);
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		mine.cancel(false, null, NOW);
		assertThatThrownBy(() -> service.cancel(MEETUP_ID, USER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_APPLICATION_NOT_ACTIVE);
	}

	@Test
	void 취소된_벙의_신청도_취소할_수_있고_승격은_없다() {
		// given
		MeetupApplication mine = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		meetup.cancel(NOW);

		// when
		service.cancel(MEETUP_ID, USER_ID, null);

		// then
		assertThat(mine.getStatus()).isEqualTo(MeetupApplicationStatus.CANCELED);
		verify(promoter, never()).promote(any(), any());
	}

	@Test
	void 내보내면_REMOVED이고_늦은_취소가_아니며_승격한다() {
		// given
		MeetupApplication target = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		given(applicationRepository.findMeetupIdById(50L)).willReturn(Optional.of(MEETUP_ID));
		given(applicationRepository.findByIdForUpdate(50L)).willReturn(Optional.of(target));
		setNow(DEADLINE.plusSeconds(1));

		// when
		service.remove(50L);

		// then
		assertThat(target.getStatus()).isEqualTo(MeetupApplicationStatus.REMOVED);
		assertThat(target.isLateCancel()).isFalse();
		assertThat(target.getCanceledAt()).isEqualTo(DEADLINE.plusSeconds(1));
		verify(promoter).promote(meetup, DEADLINE.plusSeconds(1));
	}

	@Test
	void 내보내기는_신청을_읽기_전에_벙을_잠근다() {
		// given
		MeetupApplication target = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		given(applicationRepository.findMeetupIdById(50L)).willReturn(Optional.of(MEETUP_ID));
		given(applicationRepository.findByIdForUpdate(50L)).willReturn(Optional.of(target));

		// when
		service.remove(50L);

		// then
		InOrder order = inOrder(meetupRepository, applicationRepository, promoter);
		order.verify(applicationRepository).findMeetupIdById(50L);
		order.verify(meetupRepository).findByIdForUpdate(MEETUP_ID);
		order.verify(applicationRepository).findByIdForUpdate(50L);
		order.verify(promoter).promote(meetup, NOW);
		verify(applicationRepository, never()).findById(anyLong());
	}

	@Test
	void 취소는_벙을_잠근_뒤_내_신청을_잠금_조회한다() {
		// given
		existing(MeetupApplicationStatus.CONFIRMED, NOW);

		// when
		service.cancel(MEETUP_ID, USER_ID, null);

		// then
		InOrder order = inOrder(meetupRepository, applicationRepository);
		order.verify(meetupRepository).findByIdForUpdate(MEETUP_ID);
		order.verify(applicationRepository).findByMeetupIdAndRosterMemberIdForUpdate(MEETUP_ID, ROSTER_ID);
		verify(applicationRepository, never()).findByMeetupIdAndRosterMemberId(anyLong(), anyLong());
	}

	@Test
	void 비활성_신청이나_시작한_벙은_내보낼_수_없다() {
		// given
		MeetupApplication target = existing(MeetupApplicationStatus.CONFIRMED, NOW);
		given(applicationRepository.findMeetupIdById(50L)).willReturn(Optional.of(MEETUP_ID));
		given(applicationRepository.findByIdForUpdate(50L)).willReturn(Optional.of(target));
		setNow(STARTS);

		// when, then
		assertThatThrownBy(() -> service.remove(50L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_ALREADY_STARTED);
		target.cancel(false, null, NOW);
		assertThatThrownBy(() -> service.remove(50L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETUP_APPLICATION_NOT_ACTIVE);
		assertThatThrownBy(() -> service.remove(51L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	/** 테스트마다 시계는 NOW에서 시작한다. */
	private void setNow(Instant target) {
		clock.advance(Duration.between(clock.instant(), target));
	}

	private MeetupApplication existing(MeetupApplicationStatus status, Instant appliedAt) {
		MeetupApplication application = MeetupApplication.create(meetup, roster,
				status == MeetupApplicationStatus.CONFIRMED, appliedAt);
		ReflectionTestUtils.setField(application, "id", 50L);
		given(applicationRepository.findByMeetupIdAndRosterMemberId(MEETUP_ID, ROSTER_ID))
				.willReturn(Optional.of(application));
		given(applicationRepository.findByMeetupIdAndRosterMemberIdForUpdate(MEETUP_ID, ROSTER_ID))
				.willReturn(Optional.of(application));
		return application;
	}

	private Meetup meetup(int capacity) {
		Meetup created = Meetup.create(new MeetupSpec("제목", null, "장소", STARTS, STARTS.plus(Duration.ofHours(3)),
				capacity, 24, true), null);
		ReflectionTestUtils.setField(created, "id", MEETUP_ID);
		return created;
	}

	private static RosterMember roster(long id) {
		RosterMember member = RosterMember.create("회원" + id, null, RosterSource.APP);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
