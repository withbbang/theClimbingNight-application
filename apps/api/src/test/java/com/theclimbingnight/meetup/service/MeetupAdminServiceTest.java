package com.theclimbingnight.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.dto.MeetupResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupSource;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.entity.MeetupStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetupAdminServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant STARTS = NOW.plus(Duration.ofDays(7));
	private static final Instant ENDS = STARTS.plus(Duration.ofHours(3));
	private static final long MEETUP_ID = 10L;
	private static final long ADMIN_ID = 1L;

	@Mock MeetupRepository meetupRepository;
	@Mock MeetupApplicationRepository applicationRepository;
	@Mock UserRepository userRepository;
	@Mock WaitlistPromoter promoter;
	@Mock ApplicationEventPublisher eventPublisher;

	private MeetupAdminService service;
	private User admin;

	@BeforeEach
	void setUp() {
		service = new MeetupAdminService(meetupRepository, applicationRepository, userRepository, promoter,
				eventPublisher, new MutableClock(NOW));
		admin = User.create("admin");
		ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		given(userRepository.getReferenceById(ADMIN_ID)).willReturn(admin);
		given(meetupRepository.save(any(Meetup.class))).willAnswer(inv -> {
			Meetup saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 99L);
			return saved;
		});
	}

	@Test
	void 개설하면_OPEN_APP_벙이고_주최자가_기록된다() {
		// when
		MeetupResponse response = service.create(ADMIN_ID, spec(STARTS, ENDS, 10));

		// then
		ArgumentCaptor<Meetup> saved = ArgumentCaptor.forClass(Meetup.class);
		verify(meetupRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(MeetupStatus.OPEN);
		assertThat(saved.getValue().getSource()).isEqualTo(MeetupSource.APP);
		assertThat(saved.getValue().getHost()).isSameAs(admin);
		assertThat(response.id()).isEqualTo(99L);
		assertThat(response.host().id()).isEqualTo(ADMIN_ID);
	}

	@Test
	void 시작_시각이_지금이면_400이다() {
		// when, then
		assertThatThrownBy(() -> service.create(ADMIN_ID, spec(NOW, ENDS, 10)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_START_IN_PAST));
		verify(meetupRepository, never()).save(any(Meetup.class));
	}

	@Test
	void 정원을_늘리면_대기자를_승격한다() {
		// given
		Meetup meetup = existing();
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));

		// when
		MeetupResponse response = service.update(MEETUP_ID, spec(STARTS, ENDS, 20));

		// then
		assertThat(meetup.getCapacity()).isEqualTo(20);
		assertThat(response.capacity()).isEqualTo(20);
		verify(promoter).promote(meetup, NOW);
	}

	@Test
	void 시작한_벙은_수정할_수_없다() {
		// given
		Meetup meetup = existing();
		ReflectionTestUtils.setField(meetup, "startsAt", NOW);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));

		// when, then
		assertThatThrownBy(() -> service.update(MEETUP_ID, spec(STARTS, ENDS, 20)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_ALREADY_STARTED));
	}

	@Test
	void 취소된_벙은_수정할_수_없다() {
		// given
		Meetup meetup = existing();
		meetup.cancel(NOW);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));

		// when, then
		assertThatThrownBy(() -> service.update(MEETUP_ID, spec(STARTS, ENDS, 20)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_CANCELED));
	}

	@Test
	void 없는_벙은_404다() {
		// given
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.cancel(MEETUP_ID)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 취소하면_활성_신청자에게_취소_이벤트를_발행한다() {
		// given
		Meetup meetup = existing();
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));
		MeetupApplication confirmed = application(meetup, 1L, true);
		MeetupApplication waiting = application(meetup, 2L, false);
		MeetupApplication canceled = application(meetup, 3L, true);
		canceled.cancel(false, null, NOW);
		given(applicationRepository.findAllByMeetupId(MEETUP_ID)).willReturn(List.of(confirmed, waiting, canceled));

		// when
		MeetupResponse response = service.cancel(MEETUP_ID);

		// then
		assertThat(response.status()).isEqualTo(MeetupStatus.CANCELED);
		assertThat(response.canceledAt()).isEqualTo(NOW);
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new MeetupCanceledEvent(MEETUP_ID, List.of(1L, 2L)));
	}

	@Test
	void 이미_취소된_벙은_다시_취소할_수_없다() {
		// given
		Meetup meetup = existing();
		meetup.cancel(NOW);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));

		// when, then
		assertThatThrownBy(() -> service.cancel(MEETUP_ID)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_CANCELED));
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void 끝난_벙은_취소할_수_없다() {
		// given
		Meetup meetup = existing();
		ReflectionTestUtils.setField(meetup, "startsAt", NOW.minus(Duration.ofHours(3)));
		ReflectionTestUtils.setField(meetup, "endsAt", NOW);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));

		// when, then
		assertThatThrownBy(() -> service.cancel(MEETUP_ID)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_ENDED));
	}

	@Test
	void 종료_시각이_없는_지난_벙은_취소할_수_없다() {
		// given
		Meetup meetup = existing();
		ReflectionTestUtils.setField(meetup, "startsAt", NOW.minus(Duration.ofDays(400)));
		ReflectionTestUtils.setField(meetup, "endsAt", null);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));

		// when, then
		assertThatThrownBy(() -> service.cancel(MEETUP_ID)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_ENDED));
	}

	@Test
	void 종료_시각이_없어도_시작_전인_벙은_취소할_수_있다() {
		// given
		Meetup meetup = existing();
		ReflectionTestUtils.setField(meetup, "endsAt", null);
		given(meetupRepository.findByIdForUpdate(MEETUP_ID)).willReturn(Optional.of(meetup));
		given(applicationRepository.findAllByMeetupId(MEETUP_ID)).willReturn(List.of());

		// when
		MeetupResponse response = service.cancel(MEETUP_ID);

		// then
		assertThat(response.canceledAt()).isEqualTo(NOW);
	}

	@Test
	void 복제하면_날짜만_바뀌고_나머지는_같다() {
		// given
		Meetup source = existing();
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(source));
		Instant newStart = STARTS.plus(Duration.ofDays(7));
		Instant newEnd = newStart.plus(Duration.ofHours(2));
		long otherAdmin = 2L;
		User other = User.create("other");
		ReflectionTestUtils.setField(other, "id", otherAdmin);
		given(userRepository.getReferenceById(otherAdmin)).willReturn(other);

		// when
		MeetupResponse response = service.copy(MEETUP_ID, otherAdmin, newStart, newEnd);

		// then
		ArgumentCaptor<Meetup> saved = ArgumentCaptor.forClass(Meetup.class);
		verify(meetupRepository).save(saved.capture());
		Meetup copy = saved.getValue();
		assertThat(copy.getTitle()).isEqualTo("제목");
		assertThat(copy.getDescription()).isEqualTo("설명");
		assertThat(copy.getPlaceName()).isEqualTo("장소");
		assertThat(copy.getCapacity()).isEqualTo(10);
		assertThat(copy.getLateCancelHours()).isEqualTo(12);
		assertThat(copy.isParticipantsVisible()).isFalse();
		assertThat(copy.getStartsAt()).isEqualTo(newStart);
		assertThat(copy.getEndsAt()).isEqualTo(newEnd);
		assertThat(copy.getHost()).isSameAs(other);
		assertThat(response.id()).isEqualTo(99L);
	}

	@Test
	void 복제하면_개설_이벤트를_한_번_발행한다() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(existing()));
		Instant newStart = STARTS.plus(Duration.ofDays(7));

		// when
		service.copy(MEETUP_ID, ADMIN_ID, newStart, newStart.plus(Duration.ofHours(2)));

		// then
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(1)).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new MeetupCreatedEvent(99L, ADMIN_ID));
	}

	@Test
	void 개설하면_개설_이벤트를_발행한다() {
		// when
		service.create(ADMIN_ID, spec(STARTS, ENDS, 10));

		// then
		verify(eventPublisher).publishEvent(new MeetupCreatedEvent(99L, ADMIN_ID));
	}

	@Test
	void 복제_시작_시각이_과거면_400이다() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(existing()));

		// when, then
		assertThatThrownBy(() -> service.copy(MEETUP_ID, ADMIN_ID, NOW, NOW.plusSeconds(3600)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETUP_START_IN_PAST));
	}

	@Test
	void 가져온_벙은_복제할_수_없다() {
		// given
		Meetup imported = existing();
		ReflectionTestUtils.setField(imported, "source", MeetupSource.IMPORT);
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(imported));

		// when, then
		assertThatThrownBy(() -> service.copy(MEETUP_ID, ADMIN_ID, STARTS, ENDS))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	private MeetupSpec spec(Instant startsAt, Instant endsAt, int capacity) {
		return new MeetupSpec("제목", "설명", "장소", startsAt, endsAt, capacity, 12, false);
	}

	private Meetup existing() {
		Meetup meetup = Meetup.create(spec(STARTS, ENDS, 10), admin);
		ReflectionTestUtils.setField(meetup, "id", MEETUP_ID);
		return meetup;
	}

	private MeetupApplication application(Meetup meetup, long rosterId, boolean confirmed) {
		RosterMember roster = RosterMember.create("회원" + rosterId, null, RosterSource.APP);
		ReflectionTestUtils.setField(roster, "id", rosterId);
		return MeetupApplication.create(meetup, roster, confirmed, NOW);
	}
}
