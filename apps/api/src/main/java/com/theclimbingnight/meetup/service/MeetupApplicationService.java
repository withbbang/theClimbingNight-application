package com.theclimbingnight.meetup.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.dto.MyApplicationResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동호회원의 벙 신청·취소와 운영진의 내보내기.
 * 신청 주체는 명부 항목이고, 모든 쓰기는 벙 행을 먼저 잠근 뒤 정원·신청 상태를 본다 (잠금 순서: 벙 → 신청).
 */
@Service
@Transactional(readOnly = true)
public class MeetupApplicationService {

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final UserRepository userRepository;
	private final WaitlistPromoter promoter;
	private final Clock clock;

	public MeetupApplicationService(MeetupRepository meetupRepository,
			MeetupApplicationRepository applicationRepository, RosterMemberRepository rosterMemberRepository,
			UserRepository userRepository, WaitlistPromoter promoter, Clock clock) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.userRepository = userRepository;
		this.promoter = promoter;
		this.clock = clock;
	}

	@Transactional
	public MyApplicationResponse apply(long meetupId, long userId) {
		requireActiveMember(userId);
		RosterMember roster = requireRoster(userId);
		Meetup meetup = lock(meetupId);
		Instant now = clock.instant();
		if (!meetup.isOpen()) {
			throw new BusinessException(ErrorCode.MEETUP_CANCELED);
		}
		if (meetup.hasStarted(now)) {
			throw new BusinessException(ErrorCode.MEETUP_ALREADY_STARTED);
		}
		Optional<MeetupApplication> existing = applicationRepository.findByMeetupIdAndRosterMemberId(meetupId,
				roster.getId());
		if (existing.isPresent()) {
			if (existing.get().isActive()) {
				throw new BusinessException(ErrorCode.MEETUP_ALREADY_APPLIED);
			}
			if (existing.get().getStatus() == MeetupApplicationStatus.REMOVED) {
				throw new BusinessException(ErrorCode.MEETUP_APPLICATION_REMOVED);
			}
		}
		boolean confirmed = hasFreeSeat(meetup);
		MeetupApplication application;
		if (existing.isPresent()) {
			application = existing.get();
			application.reapply(confirmed, now);
		} else {
			application = applicationRepository.save(MeetupApplication.create(meetup, roster, confirmed, now));
		}
		return toResponse(application);
	}

	/** 취소된 벙의 신청도 취소할 수 있다 (승격은 하지 않는다). */
	@Transactional
	public MyApplicationResponse cancel(long meetupId, long userId, String reason) {
		RosterMember roster = requireRoster(userId);
		Meetup meetup = lock(meetupId);
		Instant now = clock.instant();
		if (meetup.hasStarted(now)) {
			throw new BusinessException(ErrorCode.MEETUP_ALREADY_STARTED);
		}
		MeetupApplication application = applicationRepository
				.findByMeetupIdAndRosterMemberIdForUpdate(meetupId, roster.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		requireActive(application);
		boolean wasConfirmed = application.getStatus() == MeetupApplicationStatus.CONFIRMED;
		application.cancel(wasConfirmed && isLate(meetup, application, now), normalize(reason), now);
		if (wasConfirmed) {
			promoteIfOpen(meetup, now);
		}
		return toResponse(application);
	}

	@Transactional
	public void remove(long applicationId) {
		// 신청 엔티티를 먼저 읽으면 잠금 조회가 그 낡은 인스턴스를 돌려주므로, 벙 ID만 얻고 벙 → 신청 순서로 잠근다.
		long meetupId = applicationRepository.findMeetupIdById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		Meetup meetup = lock(meetupId);
		MeetupApplication application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		requireActive(application);
		Instant now = clock.instant();
		if (meetup.hasStarted(now)) {
			throw new BusinessException(ErrorCode.MEETUP_ALREADY_STARTED);
		}
		boolean wasConfirmed = application.getStatus() == MeetupApplicationStatus.CONFIRMED;
		application.remove(now);
		if (wasConfirmed) {
			promoteIfOpen(meetup, now);
		}
	}

	/** 확정자 본인 취소만 대상: 기준 시각 이후(정각 포함)이고, 기준 시각 전에 확정된 사람이어야 늦은 취소다. */
	private static boolean isLate(Meetup meetup, MeetupApplication application, Instant now) {
		Instant deadline = meetup.lateCancelDeadline();
		return !now.isBefore(deadline) && application.getConfirmedAt().isBefore(deadline);
	}

	/** 정원이 없는 벙(가져온 기록)은 제한 없이 확정한다. */
	private boolean hasFreeSeat(Meetup meetup) {
		if (meetup.getCapacity() == null) {
			return true;
		}
		return applicationRepository.countByMeetupIdAndStatus(meetup.getId(), MeetupApplicationStatus.CONFIRMED)
				< meetup.getCapacity();
	}

	private void promoteIfOpen(Meetup meetup, Instant now) {
		if (meetup.isOpen()) {
			promoter.promote(meetup, now);
		}
	}

	private MyApplicationResponse toResponse(MeetupApplication application) {
		if (application.getStatus() != MeetupApplicationStatus.WAITLISTED) {
			return MyApplicationResponse.of(application, null);
		}
		List<MeetupApplication> waiting = applicationRepository.findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(
				application.getMeetup().getId(), MeetupApplicationStatus.WAITLISTED);
		return MyApplicationResponse.of(application, WaitlistPositions.of(application, waiting));
	}

	/**
	 * 사용자 행을 먼저 잠그고(잠금 순서: 사용자 → 벙 → 신청) 활성 동호회원인지 다시 확인한다.
	 * 인터셉터의 역할 검사 뒤 자격 해제·탈퇴가 끼어들어도, 그 트랜잭션이 끝난 뒤의 상태로 판단해 신청이 새어 들어오지 않는다.
	 */
	private void requireActiveMember(long userId) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FORBIDDEN));
		if (!user.isActive() || !user.getRole().atLeast(Role.MEMBER)) {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}
	}

	private RosterMember requireRoster(long userId) {
		return rosterMemberRepository.findByUserId(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETUP_ROSTER_REQUIRED));
	}

	private Meetup lock(long meetupId) {
		return meetupRepository.findByIdForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	private static void requireActive(MeetupApplication application) {
		if (!application.isActive()) {
			throw new BusinessException(ErrorCode.MEETUP_APPLICATION_NOT_ACTIVE);
		}
	}

	private static String normalize(String reason) {
		return reason == null || reason.isBlank() ? null : reason.strip();
	}
}
