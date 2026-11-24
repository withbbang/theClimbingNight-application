package com.theclimbingnight.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.penalty.dto.PenaltyResponse;
import com.theclimbingnight.penalty.dto.RosterPenaltiesResponse;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class PenaltyServiceTest {

	private static final Instant NOW = OffsetDateTime.parse("2026-05-31T00:00:00+09:00").toInstant();
	private static final long ADMIN_ID = 1L;
	private static final long APPLICATION_ID = 100L;

	@Mock PenaltyRepository penaltyRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock RosterMemberRepository rosterMemberRepository;
	@Mock UserRepository userRepository;

	private PenaltyService service;
	private User admin;
	private Meetup meetup;
	private RosterMember roster;

	@BeforeEach
	void setUp() {
		service = new PenaltyService(penaltyRepository, applicationRepository, rosterMemberRepository, userRepository,
				new MutableClock(NOW));
		admin = User.create("admin");
		ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
		given(userRepository.getReferenceById(ADMIN_ID)).willReturn(admin);
		Instant starts = Instant.parse("2026-05-20T10:00:00Z");
		meetup = Meetup.create(new MeetupSpec("정기벙", null, "장소", starts, starts.plusSeconds(10800), 10, 24, true),
				null);
		ReflectionTestUtils.setField(meetup, "id", 10L);
		roster = RosterMember.create("회원", null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", 5L);
		given(penaltyRepository.save(any(Penalty.class))).willAnswer(invocation -> {
			Penalty saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 300L);
			return saved;
		});
	}

	private MeetupApplication canceledApplication(boolean late) {
		MeetupApplication application = MeetupApplication.create(meetup, roster, true, NOW.minusSeconds(86400 * 3));
		application.cancel(late, "야근", NOW.minusSeconds(3600));
		ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
		given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.of(application));
		return application;
	}

	private Penalty penalty(long id, Meetup target, PenaltyType type) {
		Penalty penalty = Penalty.create(roster, target, type, admin);
		ReflectionTestUtils.setField(penalty, "id", id);
		return penalty;
	}

	@Test
	void 늦은_취소_건에만_블랙리스트를_매긴다() {
		// given
		canceledApplication(false);

		// when, then
		assertThatThrownBy(() -> service.addLateCancel(APPLICATION_ID, ADMIN_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PENALTY_NOT_LATE_CANCEL);
		verify(penaltyRepository, never()).save(any());
	}

	@Test
	void 늦은_취소_건에_블랙리스트를_매기면_LATE_CANCEL로_저장된다() {
		// given
		canceledApplication(true);
		given(penaltyRepository.findActive(10L, 5L, PenaltyType.LATE_CANCEL)).willReturn(Optional.empty());

		// when
		PenaltyResponse response = service.addLateCancel(APPLICATION_ID, ADMIN_ID);

		// then
		ArgumentCaptor<Penalty> captor = ArgumentCaptor.forClass(Penalty.class);
		verify(penaltyRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(PenaltyType.LATE_CANCEL);
		assertThat(captor.getValue().getCreatedBy()).isSameAs(admin);
		assertThat(response.id()).isEqualTo(300L);
		assertThat(response.meetupId()).isEqualTo(10L);
		assertThat(response.meetupTitle()).isEqualTo("정기벙");
		assertThat(response.active()).isTrue();
	}

	@Test
	void 같은_늦은_취소에_두_번_매기면_409다() {
		// given
		canceledApplication(true);
		given(penaltyRepository.findActive(10L, 5L, PenaltyType.LATE_CANCEL))
				.willReturn(Optional.of(penalty(1L, meetup, PenaltyType.LATE_CANCEL)));

		// when, then
		assertThatThrownBy(() -> service.addLateCancel(APPLICATION_ID, ADMIN_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PENALTY_ALREADY_EXISTS);
		verify(penaltyRepository, never()).save(any());
	}

	@Test
	void 없는_신청에_매기면_404다() {
		// given
		given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.addLateCancel(APPLICATION_ID, ADMIN_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	@Test
	void 취소하면_사유와_취소자가_남는다() {
		// given
		Penalty penalty = penalty(7L, meetup, PenaltyType.NO_SHOW);
		given(penaltyRepository.findByIdForUpdate(7L)).willReturn(Optional.of(penalty));

		// when
		PenaltyResponse response = service.revoke(7L, ADMIN_ID, "  착오  ");

		// then
		assertThat(penalty.getRevokedBy()).isSameAs(admin);
		assertThat(penalty.getRevokeReason()).isEqualTo("착오");
		assertThat(penalty.getRevokedAt()).isEqualTo(NOW);
		assertThat(response.active()).isFalse();
		assertThat(response.revokeReason()).isEqualTo("착오");
	}

	@Test
	void 이미_취소된_기록을_취소하면_409다() {
		// given
		Penalty penalty = penalty(7L, meetup, PenaltyType.NO_SHOW);
		penalty.revoke(admin, "처음", NOW.minusSeconds(60));
		given(penaltyRepository.findByIdForUpdate(7L)).willReturn(Optional.of(penalty));

		// when, then
		assertThatThrownBy(() -> service.revoke(7L, ADMIN_ID, "다시"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PENALTY_ALREADY_REVOKED);
		assertThat(penalty.getRevokeReason()).isEqualTo("처음");
	}

	@Test
	void 없는_기록을_취소하면_404다() {
		// given
		given(penaltyRepository.findByIdForUpdate(anyLong())).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.revoke(7L, ADMIN_ID, "사유"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	@Test
	void 최근_3개월은_서울_기준_석_달_전_같은_시각부터다() {
		// when
		Instant recentFrom = service.recentFrom();

		// then
		assertThat(recentFrom).isEqualTo(OffsetDateTime.parse("2026-02-28T00:00:00+09:00").toInstant());
	}

	@Test
	void 명부가_없으면_내_기록은_빈_목록이다() {
		// given
		given(rosterMemberRepository.findByUserId(9L)).willReturn(Optional.empty());

		// when
		List<PenaltyResponse> result = service.mine(9L);

		// then
		assertThat(result).isEmpty();
		verify(penaltyRepository, never()).findAllByRosterMemberIdOrderByIdDesc(anyLong());
	}

	@Test
	void 내_기록에는_취소된_기록도_비활성으로_나온다() {
		// given
		given(rosterMemberRepository.findByUserId(9L)).willReturn(Optional.of(roster));
		Penalty revoked = penalty(2L, meetup, PenaltyType.NO_SHOW);
		revoked.revoke(admin, "착오", NOW);
		Penalty active = penalty(1L, meetup, PenaltyType.LATE_CANCEL);
		given(penaltyRepository.findAllByRosterMemberIdOrderByIdDesc(5L)).willReturn(List.of(revoked, active));

		// when
		List<PenaltyResponse> result = service.mine(9L);

		// then
		assertThat(result).extracting(PenaltyResponse::id, PenaltyResponse::active).containsExactly(
				tuple(2L, false), tuple(1L, true));
	}

	@Test
	void 명부별_기록은_활성만_누적과_최근_3개월로_센다() {
		// given
		given(rosterMemberRepository.findById(5L)).willReturn(Optional.of(roster));
		Instant boundary = OffsetDateTime.parse("2026-02-28T00:00:00+09:00").toInstant();
		Meetup old = Meetup.create(new MeetupSpec("옛벙", null, "장소", boundary.minusSeconds(1),
				boundary.plusSeconds(3600), 10, 24, true), null);
		ReflectionTestUtils.setField(old, "id", 11L);
		Meetup edge = Meetup.create(new MeetupSpec("경계벙", null, "장소", boundary, boundary.plusSeconds(3600), 10,
				24, true), null);
		ReflectionTestUtils.setField(edge, "id", 12L);
		Penalty revoked = penalty(4L, meetup, PenaltyType.NO_SHOW);
		revoked.revoke(admin, "착오", NOW);
		given(penaltyRepository.findAllByRosterMemberIdOrderByIdDesc(5L)).willReturn(List.of(revoked,
				penalty(3L, meetup, PenaltyType.LATE_CANCEL), penalty(2L, edge, PenaltyType.NO_SHOW),
				penalty(1L, old, PenaltyType.NO_SHOW)));

		// when
		RosterPenaltiesResponse response = service.ofRoster(5L);

		// then
		assertThat(response.rosterMemberId()).isEqualTo(5L);
		assertThat(response.somoimNickname()).isEqualTo("회원");
		assertThat(response.totalCount()).isEqualTo(3);
		assertThat(response.recentCount()).isEqualTo(2);
		assertThat(response.penalties()).hasSize(4);
	}

	@Test
	void 없는_명부의_기록을_보면_404다() {
		// given
		given(rosterMemberRepository.findById(5L)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.ofRoster(5L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}
}
