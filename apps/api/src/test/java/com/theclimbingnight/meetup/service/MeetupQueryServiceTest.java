package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.dto.MeetupDetailResponse;
import com.theclimbingnight.meetup.dto.MeetupSummaryResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.meetup.repository.MeetupStatusCount;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class MeetupQueryServiceTest {

	private static final Instant STARTS = Instant.parse("2026-12-10T10:00:00Z");
	private static final long MEETUP_ID = 10L;
	private static final long USER_ID = 7L;
	private static final long ROSTER_ID = 4L;

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock RosterMemberRepository rosterMemberRepository;

	private MeetupQueryService service;
	private RosterMember me;

	@BeforeEach
	void setUp() {
		service = new MeetupQueryService(meetupRepository, applicationRepository, rosterMemberRepository);
		me = roster(ROSTER_ID);
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.of(me));
	}

	@Test
	void 서울_기준_월_첫날_0시와_말일_밤에_시작하는_벙이_포함된다() {
		// given
		given(meetupRepository.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(any(),
				any())).willReturn(List.of());

		// when
		service.list(USER_ID, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));

		// then
		verify(meetupRepository).findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
				Instant.parse("2026-11-30T15:00:00Z"), Instant.parse("2026-12-31T15:00:00Z"));
	}

	@Test
	void 기간이_거꾸로거나_62일을_넘으면_400이다() {
		// given
		LocalDate from = LocalDate.of(2026, 12, 1);
		given(meetupRepository.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(any(),
				any())).willReturn(List.of());

		// when, then
		assertThatThrownBy(() -> service.list(USER_ID, from, from.minusDays(1)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		assertThatThrownBy(() -> service.list(USER_ID, from, from.plusDays(63)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		assertThat(service.list(USER_ID, from, from.plusDays(62))).isEmpty();
	}

	@Test
	void 목록에_확정_대기_인원과_내_상태가_나온다() {
		// given
		Meetup first = meetup(MEETUP_ID, true);
		Meetup second = meetup(11L, true);
		given(meetupRepository.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(any(),
				any())).willReturn(List.of(first, second));
		given(applicationRepository.countByMeetupIds(List.of(10L, 11L))).willReturn(List.of(
				new MeetupStatusCount(10L, MeetupApplicationStatus.CONFIRMED, 4),
				new MeetupStatusCount(10L, MeetupApplicationStatus.WAITLISTED, 2)));
		MeetupApplication mine = application(first, me, MeetupApplicationStatus.CANCELED, 1);
		given(applicationRepository.findAllByMeetupIdInAndRosterMemberId(List.of(10L, 11L), ROSTER_ID))
				.willReturn(List.of(mine));

		// when
		List<MeetupSummaryResponse> result = service.list(USER_ID, LocalDate.of(2026, 12, 1),
				LocalDate.of(2026, 12, 31));

		// then
		assertThat(result).extracting(MeetupSummaryResponse::id).containsExactly(10L, 11L);
		assertThat(result.get(0).confirmedCount()).isEqualTo(4);
		assertThat(result.get(0).waitlistCount()).isEqualTo(2);
		assertThat(result.get(0).myStatus()).isEqualTo(MeetupApplicationStatus.CANCELED);
		assertThat(result.get(1).confirmedCount()).isZero();
		assertThat(result.get(1).waitlistCount()).isZero();
		assertThat(result.get(1).myStatus()).isNull();
	}

	@Test
	void 명부가_없으면_목록의_내_상태는_null이다() {
		// given
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
		given(meetupRepository.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(any(),
				any())).willReturn(List.of(meetup(MEETUP_ID, true)));

		// when
		List<MeetupSummaryResponse> result = service.list(USER_ID, LocalDate.of(2026, 12, 1),
				LocalDate.of(2026, 12, 31));

		// then
		assertThat(result.get(0).myStatus()).isNull();
	}

	@Test
	void 명단_공개면_확정_순서와_대기_순번이_나온다() {
		// given
		stubDetail(meetup(MEETUP_ID, true));

		// when
		MeetupDetailResponse response = service.get(MEETUP_ID, USER_ID, Role.MEMBER);

		// then
		assertThat(response.confirmedCount()).isEqualTo(2);
		assertThat(response.waitlistCount()).isEqualTo(2);
		assertThat(response.participants()).extracting("somoimNickname", "status", "order").containsExactly(
				org.assertj.core.groups.Tuple.tuple("회원1", MeetupApplicationStatus.CONFIRMED, 1),
				org.assertj.core.groups.Tuple.tuple("회원2", MeetupApplicationStatus.CONFIRMED, 2),
				org.assertj.core.groups.Tuple.tuple("회원3", MeetupApplicationStatus.WAITLISTED, 1),
				org.assertj.core.groups.Tuple.tuple("회원4", MeetupApplicationStatus.WAITLISTED, 2));
		assertThat(response.my().status()).isEqualTo(MeetupApplicationStatus.WAITLISTED);
		assertThat(response.my().waitlistPosition()).isEqualTo(2);
	}

	@Test
	void 명단_비공개면_동호회원에게_명단이_없고_인원과_내_순번만_있다() {
		// given
		stubDetail(meetup(MEETUP_ID, false));

		// when
		MeetupDetailResponse response = service.get(MEETUP_ID, USER_ID, Role.MEMBER);

		// then
		assertThat(response.participants()).isNull();
		assertThat(response.confirmedCount()).isEqualTo(2);
		assertThat(response.waitlistCount()).isEqualTo(2);
		assertThat(response.my().waitlistPosition()).isEqualTo(2);
	}

	@Test
	void 명단_비공개여도_운영진은_명단을_본다() {
		// given
		stubDetail(meetup(MEETUP_ID, false));

		// when
		MeetupDetailResponse response = service.get(MEETUP_ID, USER_ID, Role.ADMIN);

		// then
		assertThat(response.participants()).hasSize(4);
	}

	@Test
	void 명부가_없는_운영진도_상세를_본다() {
		// given
		given(rosterMemberRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
		Meetup meetup = meetup(MEETUP_ID, true);
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup));
		given(applicationRepository.countByMeetupIds(List.of(MEETUP_ID))).willReturn(List.of());
		given(applicationRepository.findAllWithRosterByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.CONFIRMED)).willReturn(List.of());
		given(applicationRepository.findAllWithRosterByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.WAITLISTED)).willReturn(List.of());

		// when
		MeetupDetailResponse response = service.get(MEETUP_ID, USER_ID, Role.ADMIN);

		// then
		assertThat(response.my()).isNull();
		assertThat(response.participants()).isEmpty();
		verify(applicationRepository, never()).findByMeetupIdAndRosterMemberId(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void 없는_벙이면_404이다() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.get(MEETUP_ID, USER_ID, Role.MEMBER))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	/** 확정 회원1·2, 대기 회원3·4 (내가 4). */
	private void stubDetail(Meetup meetup) {
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup));
		given(applicationRepository.countByMeetupIds(List.of(MEETUP_ID))).willReturn(List.of(
				new MeetupStatusCount(MEETUP_ID, MeetupApplicationStatus.CONFIRMED, 2),
				new MeetupStatusCount(MEETUP_ID, MeetupApplicationStatus.WAITLISTED, 2)));
		MeetupApplication mine = application(meetup, me, MeetupApplicationStatus.WAITLISTED, 4);
		given(applicationRepository.findByMeetupIdAndRosterMemberId(MEETUP_ID, ROSTER_ID))
				.willReturn(Optional.of(mine));
		given(applicationRepository.findAllWithRosterByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.CONFIRMED)).willReturn(List.of(
						application(meetup, roster(1L), MeetupApplicationStatus.CONFIRMED, 1),
						application(meetup, roster(2L), MeetupApplicationStatus.CONFIRMED, 2)));
		given(applicationRepository.findAllWithRosterByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(MEETUP_ID,
				MeetupApplicationStatus.WAITLISTED)).willReturn(List.of(
						application(meetup, roster(3L), MeetupApplicationStatus.WAITLISTED, 3), mine));
	}

	private static MeetupApplication application(Meetup meetup, RosterMember roster,
			MeetupApplicationStatus status, long id) {
		MeetupApplication application = MeetupApplication.create(meetup, roster,
				status == MeetupApplicationStatus.CONFIRMED, STARTS.minusSeconds(1000 - id));
		ReflectionTestUtils.setField(application, "id", id);
		ReflectionTestUtils.setField(application, "status", status);
		return application;
	}

	private static Meetup meetup(long id, boolean participantsVisible) {
		Meetup created = Meetup.create(new MeetupSpec("제목", null, "장소", STARTS, STARTS.plus(Duration.ofHours(3)), 2,
				24, participantsVisible), null);
		ReflectionTestUtils.setField(created, "id", id);
		return created;
	}

	private static RosterMember roster(long id) {
		RosterMember member = RosterMember.create("회원" + id, null, RosterSource.APP);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
