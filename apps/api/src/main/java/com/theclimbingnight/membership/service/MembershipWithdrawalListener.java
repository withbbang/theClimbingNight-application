package com.theclimbingnight.membership.service;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 트랜잭션 안에서 동기 실행된다. 잠금 순서(신청 → 명부)를 지키려고 신청을 먼저 처리한다. */
@Component
public class MembershipWithdrawalListener {

	static final String REJECT_REASON = "회원 탈퇴로 신청이 종료되었습니다.";

	private final MembershipRequestRepository membershipRequestRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final Clock clock;

	public MembershipWithdrawalListener(MembershipRequestRepository membershipRequestRepository,
			RosterMemberRepository rosterMemberRepository, Clock clock) {
		this.membershipRequestRepository = membershipRequestRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.clock = clock;
	}

	@EventListener
	@Transactional
	public void on(UserWithdrawnEvent event) {
		Instant now = clock.instant();
		for (MembershipRequest request : membershipRequestRepository
				.findAllByUserIdAndStatus(event.userId(), MembershipRequestStatus.PENDING)) {
			request.reject(REJECT_REASON, null, now);
		}
		rosterMemberRepository.unlinkUser(event.userId());
	}
}
