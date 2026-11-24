package com.theclimbingnight.meetup.service;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴하면 아직 시작하지 않은 벙 신청을 취소하고(늦은 취소 아님), 확정자였으면 대기자를 승격한다.
 * 탈퇴 트랜잭션 안에서 동기 실행된다.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}로 계획 2의 {@code MembershipWithdrawalListener}보다 먼저 돈다.
 * 그 리스너가 {@code unlinkUser}로 명부 연결을 끊으면 {@code findByUserId}로 명부를 찾을 수 없고,
 * {@code clearAutomatically}가 영속성 컨텍스트를 비워 이 리스너가 읽어 둔 신청 변경도 사라질 수 있다.
 * 또 잠금 순서(사용자 → 벙 → 신청 → 명부)상 벙 잠금이 명부 갱신보다 앞서야 한다.
 * 여러 벙을 잠글 때는 벙 ID 순서로 잠가 동시 탈퇴끼리 교착하지 않게 한다.
 *
 * <p>대상은 벙 ID만 먼저 조회하고, 벙을 잠근 뒤 신청을 잠금 조회로 다시 읽어 상태를 확인한다.
 * 엔티티를 미리 읽어 두면 Hibernate가 잠금 조회 때 새로 고치지 않아, 그 사이 운영진이 내보낸 신청을
 * CANCELED로 덮거나 취소된 벙에서 승격할 수 있다.
 */
@Component
public class MeetupWithdrawalListener {

	static final String CANCEL_REASON = "회원 탈퇴";

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final WaitlistPromoter promoter;
	private final Clock clock;

	public MeetupWithdrawalListener(MeetupRepository meetupRepository,
			MeetupApplicationRepository applicationRepository, RosterMemberRepository rosterMemberRepository,
			WaitlistPromoter promoter, Clock clock) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.promoter = promoter;
		this.clock = clock;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		Optional<RosterMember> roster = rosterMemberRepository.findByUserId(event.userId());
		if (roster.isEmpty()) {
			return;
		}
		long rosterId = roster.get().getId();
		Instant now = clock.instant();
		for (long meetupId : applicationRepository.findActiveUpcomingMeetupIds(rosterId, now)) {
			Optional<Meetup> meetup = meetupRepository.findByIdForUpdate(meetupId);
			if (meetup.isEmpty() || !meetup.get().isOpen() || meetup.get().hasStarted(now)) {
				continue;
			}
			Optional<MeetupApplication> application = applicationRepository
					.findByMeetupIdAndRosterMemberIdForUpdate(meetupId, rosterId);
			if (application.isEmpty() || !application.get().isActive()) {
				continue;
			}
			boolean wasConfirmed = application.get().getStatus() == MeetupApplicationStatus.CONFIRMED;
			application.get().cancel(false, CANCEL_REASON, now);
			if (wasConfirmed) {
				promoter.promote(meetup.get(), now);
			}
		}
	}
}
