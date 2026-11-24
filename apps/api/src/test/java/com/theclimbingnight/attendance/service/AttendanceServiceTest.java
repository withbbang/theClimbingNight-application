package com.theclimbingnight.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.attendance.dto.AttendanceResponse;
import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.attendance.entity.AttendanceSource;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

	private static final Instant NOW = Instant.parse("2026-02-01T00:00:00Z");
	private static final long ADMIN_ID = 1L;

	@Mock AttendanceRepository attendanceRepository;
	@Mock MeetupRepository meetupRepository;
	@Mock PenaltyRepository penaltyRepository;
	@Mock UserRepository userRepository;

	private AttendanceService service;
	private User admin;
	private Meetup meetup;
	private RosterMember roster;
	private Attendance attendance;

	@BeforeEach
	void setUp() {
		service = new AttendanceService(attendanceRepository, meetupRepository, penaltyRepository, userRepository,
				new MutableClock(NOW));
		admin = User.create("admin");
		ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		given(userRepository.findByIdForUpdate(ADMIN_ID)).willReturn(Optional.of(admin));
		Instant starts = Instant.parse("2026-01-01T10:00:00Z");
		meetup = Meetup.create(new MeetupSpec("제목", null, "장소", starts, starts.plusSeconds(10800), 10, 24, true),
				null);
		ReflectionTestUtils.setField(meetup, "id", 10L);
		roster = RosterMember.create("회원", null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", 5L);
		attendance = Attendance.auto(meetup, roster);
		ReflectionTestUtils.setField(attendance, "id", 100L);
		given(attendanceRepository.findMeetupIdById(100L)).willReturn(Optional.of(10L));
		given(meetupRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meetup));
		given(attendanceRepository.findByIdForUpdate(100L)).willReturn(Optional.of(attendance));
	}

	@Test
	void 노쇼로_바꾸면_노쇼_블랙리스트가_생긴다() {
		// given
		given(penaltyRepository.findActiveForUpdate(10L, 5L, PenaltyType.NO_SHOW)).willReturn(Optional.empty());

		// when
		AttendanceResponse response = service.correct(100L, ADMIN_ID, AttendanceResult.NO_SHOW);

		// then
		assertThat(response.result()).isEqualTo(AttendanceResult.NO_SHOW);
		assertThat(response.source()).isEqualTo(AttendanceSource.ADMIN);
		assertThat(attendance.getRecordedBy()).isSameAs(admin);
		ArgumentCaptor<Penalty> saved = ArgumentCaptor.forClass(Penalty.class);
		verify(penaltyRepository).save(saved.capture());
		assertThat(saved.getValue().getType()).isEqualTo(PenaltyType.NO_SHOW);
		assertThat(saved.getValue().getCreatedBy()).isSameAs(admin);
		assertThat(saved.getValue().getRosterMember()).isSameAs(roster);
		assertThat(saved.getValue().getMeetup()).isSameAs(meetup);
	}

	@Test
	void 노쇼에서_참석으로_되돌리면_노쇼_기록이_취소된다() {
		// given
		attendance.correct(AttendanceResult.NO_SHOW, admin);
		Penalty penalty = Penalty.create(roster, meetup, PenaltyType.NO_SHOW, admin);
		given(penaltyRepository.findActiveForUpdate(10L, 5L, PenaltyType.NO_SHOW)).willReturn(Optional.of(penalty));

		// when
		service.correct(100L, ADMIN_ID, AttendanceResult.ATTENDED);

		// then
		assertThat(penalty.isActive()).isFalse();
		assertThat(penalty.getRevokedBy()).isSameAs(admin);
		assertThat(penalty.getRevokeReason()).isEqualTo("참석 보정으로 취소");
		assertThat(penalty.getRevokedAt()).isEqualTo(NOW);
		verify(penaltyRepository, never()).save(any(Penalty.class));
	}

	@Test
	void 노쇼_참석_노쇼로_바꿔도_활성_노쇼는_하나다() {
		// given
		Penalty[] holder = new Penalty[1];
		given(penaltyRepository.findActiveForUpdate(10L, 5L, PenaltyType.NO_SHOW)).willAnswer(
				inv -> Optional.ofNullable(holder[0] != null && holder[0].isActive() ? holder[0] : null));
		given(penaltyRepository.save(any(Penalty.class))).willAnswer(inv -> {
			holder[0] = inv.getArgument(0);
			return holder[0];
		});

		// when
		service.correct(100L, ADMIN_ID, AttendanceResult.NO_SHOW);
		Penalty first = holder[0];
		service.correct(100L, ADMIN_ID, AttendanceResult.ATTENDED);
		service.correct(100L, ADMIN_ID, AttendanceResult.NO_SHOW);

		// then
		assertThat(first.isActive()).isFalse();
		assertThat(holder[0]).isNotSameAs(first);
		assertThat(holder[0].isActive()).isTrue();
	}

	@Test
	void 불참으로_바꾸면_블랙리스트가_생기지_않는다() {
		// when
		AttendanceResponse response = service.correct(100L, ADMIN_ID, AttendanceResult.EXCUSED);

		// then
		assertThat(response.result()).isEqualTo(AttendanceResult.EXCUSED);
		verify(penaltyRepository, never()).save(any(Penalty.class));
	}

	@Test
	void 같은_결과로_바꾸면_아무것도_바뀌지_않는다() {
		// when
		AttendanceResponse response = service.correct(100L, ADMIN_ID, AttendanceResult.ATTENDED);

		// then
		assertThat(response.source()).isEqualTo(AttendanceSource.AUTO);
		assertThat(attendance.getRecordedBy()).isNull();
		verify(penaltyRepository, never()).save(any(Penalty.class));
	}

	@Test
	void 없는_참석이면_404다() {
		// when, then
		assertThatThrownBy(() -> service.correct(999L, ADMIN_ID, AttendanceResult.NO_SHOW))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 벙을_먼저_잠그고_참석을_잠근_뒤_블랙리스트를_잠근다() {
		// given
		given(penaltyRepository.findActiveForUpdate(10L, 5L, PenaltyType.NO_SHOW)).willReturn(Optional.empty());

		// when
		service.correct(100L, ADMIN_ID, AttendanceResult.NO_SHOW);

		// then
		InOrder order = inOrder(attendanceRepository, meetupRepository, penaltyRepository);
		order.verify(attendanceRepository).findMeetupIdById(100L);
		order.verify(meetupRepository).findByIdForUpdate(10L);
		order.verify(attendanceRepository).findByIdForUpdate(100L);
		order.verify(penaltyRepository).findActiveForUpdate(10L, 5L, PenaltyType.NO_SHOW);
		order.verify(penaltyRepository).save(any(Penalty.class));
	}

	@Test
	void 운영진_행을_벙보다_먼저_잠근다() {
		// given
		given(penaltyRepository.findActiveForUpdate(10L, 5L, PenaltyType.NO_SHOW)).willReturn(Optional.empty());

		// when
		service.correct(100L, ADMIN_ID, AttendanceResult.NO_SHOW);

		// then
		InOrder order = inOrder(userRepository, meetupRepository, attendanceRepository);
		order.verify(userRepository).findByIdForUpdate(ADMIN_ID);
		order.verify(meetupRepository).findByIdForUpdate(10L);
		order.verify(attendanceRepository).findByIdForUpdate(100L);
	}

	@Test
	void 운영진이_없으면_인증_필요_오류다() {
		// given
		given(userRepository.findByIdForUpdate(ADMIN_ID)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.correct(100L, ADMIN_ID, AttendanceResult.NO_SHOW))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED);
		verify(meetupRepository, never()).findByIdForUpdate(10L);
	}
}
