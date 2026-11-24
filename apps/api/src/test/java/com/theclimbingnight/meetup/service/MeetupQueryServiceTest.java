package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.dto.MeetupApplicationAdminResponse;
import com.theclimbingnight.meetup.dto.MeetupDetailResponse;
import com.theclimbingnight.meetup.dto.MeetupSummaryResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.meetup.repository.MeetupStatusCount;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.repository.PenaltyCount;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.penalty.service.PenaltyService;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
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
	@Mock AttendanceRepository attendanceRepository;
	@Mock PenaltyRepository penaltyRepository;
	@Mock PenaltyService penaltyService;

	private MeetupQueryService service;
	private RosterMember me;

	@BeforeEach
	void setUp() {
		service = new MeetupQueryService(meetupRepository, applicationRepository, rosterMemberRepository,
				attendanceRepository, penaltyRepository, penaltyService);
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

	@Test
	void 운영진_신청자_목록은_확정_대기_취소_순이고_참석과_블랙리스트_횟수가_붙는다() {
		// given
		Meetup meetup = meetup(MEETUP_ID, false);
		given(meetupRepository.existsById(MEETUP_ID)).willReturn(true);
		Instant base = STARTS.minusSeconds(100000);
		RosterMember linked = roster(1L);
		User user = User.create("연결회원");
		ReflectionTestUtils.setField(user, "id", 70L);
		linked.linkTo(user);
		// 확정: 2번이 먼저 확정, 1번이 나중(대기에서 승격)
		MeetupApplication confirmedLate = adminApplication(meetup, linked, 1L, base, base.plusSeconds(500));
		MeetupApplication confirmedEarly = adminApplication(meetup, roster(2L), 2L, base.plusSeconds(10),
				base.plusSeconds(10));
		// 대기: 4번이 먼저 신청
		MeetupApplication waitingSecond = adminApplication(meetup, roster(3L), 3L, base.plusSeconds(40), null);
		MeetupApplication waitingFirst = adminApplication(meetup, roster(4L), 4L, base.plusSeconds(30), null);
		// 취소·내보내기: canceledAt 순
		MeetupApplication canceledLater = adminApplication(meetup, roster(5L), 5L, base.plusSeconds(1),
				base.plusSeconds(1));
		canceledLater.cancel(true, "야근", base.plusSeconds(900));
		MeetupApplication removedEarlier = adminApplication(meetup, roster(6L), 6L, base.plusSeconds(2),
				base.plusSeconds(2));
		removedEarlier.remove(base.plusSeconds(800));
		given(applicationRepository.findAllWithRosterByMeetupId(MEETUP_ID)).willReturn(List.of(canceledLater,
				waitingSecond, confirmedLate, removedEarlier, waitingFirst, confirmedEarly));
		Attendance attendance = Attendance.auto(meetup, roster(2L));
		ReflectionTestUtils.setField(attendance, "id", 900L);
		attendance.correct(AttendanceResult.NO_SHOW, null);
		given(attendanceRepository.findAllByMeetupId(MEETUP_ID)).willReturn(List.of(attendance));
		Penalty lateCancel = Penalty.create(roster(5L), meetup, PenaltyType.LATE_CANCEL, null);
		Penalty noShow = Penalty.create(roster(2L), meetup, PenaltyType.NO_SHOW, null);
		given(penaltyRepository.findAllByMeetupIdAndRevokedAtIsNull(MEETUP_ID)).willReturn(List.of(lateCancel, noShow));
		Instant recentFrom = Instant.parse("2026-08-24T00:00:00Z");
		given(penaltyService.recentFrom()).willReturn(recentFrom);
		given(penaltyRepository.countActiveByRosterIds(any(), eq(recentFrom)))
				.willReturn(List.of(new PenaltyCount(2L, 3, 1), new PenaltyCount(5L, 1, 1)));

		// when
		List<MeetupApplicationAdminResponse> result = service.applications(MEETUP_ID);

		// then
		assertThat(result).extracting(MeetupApplicationAdminResponse::id).containsExactly(2L, 1L, 4L, 3L, 6L, 5L);
		MeetupApplicationAdminResponse first = result.get(0);
		assertThat(first.attendanceId()).isEqualTo(900L);
		assertThat(first.attendanceResult()).isEqualTo(AttendanceResult.NO_SHOW);
		assertThat(first.penaltyTotal()).isEqualTo(3);
		assertThat(first.penaltyRecent()).isEqualTo(1);
		assertThat(first.lateCancelPenalized()).isFalse();
		MeetupApplicationAdminResponse second = result.get(1);
		assertThat(second.userId()).isEqualTo(70L);
		assertThat(second.somoimNickname()).isEqualTo("회원1");
		assertThat(second.attendanceId()).isNull();
		assertThat(second.penaltyTotal()).isZero();
		MeetupApplicationAdminResponse last = result.get(5);
		assertThat(last.status()).isEqualTo(MeetupApplicationStatus.CANCELED);
		assertThat(last.lateCancel()).isTrue();
		assertThat(last.cancelReason()).isEqualTo("야근");
		assertThat(last.lateCancelPenalized()).isTrue();
		assertThat(last.userId()).isNull();
		assertThat(result.get(4).status()).isEqualTo(MeetupApplicationStatus.REMOVED);
	}

	@Test
	void 없는_벙의_신청자_목록은_404다() {
		// given
		given(meetupRepository.existsById(MEETUP_ID)).willReturn(false);

		// when, then
		assertThatThrownBy(() -> service.applications(MEETUP_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	@Test
	void 신청이_없는_벙의_신청자_목록은_비어_있다() {
		// given
		given(meetupRepository.existsById(MEETUP_ID)).willReturn(true);
		given(applicationRepository.findAllWithRosterByMeetupId(MEETUP_ID)).willReturn(List.of());

		// when
		List<MeetupApplicationAdminResponse> result = service.applications(MEETUP_ID);

		// then
		assertThat(result).isEmpty();
		verify(penaltyRepository, never()).countActiveByRosterIds(any(), any());
	}

	/** 신청 시각과 확정 시각(null이면 대기)을 직접 정한 신청. */
	private static MeetupApplication adminApplication(Meetup meetup, RosterMember roster, long id, Instant appliedAt,
			Instant confirmedAt) {
		MeetupApplication application = MeetupApplication.create(meetup, roster, false, appliedAt);
		if (confirmedAt != null) {
			application.confirm(confirmedAt);
		}
		ReflectionTestUtils.setField(application, "id", id);
		return application;
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
