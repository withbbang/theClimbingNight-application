package com.theclimbingnight.membership.service;

import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 트랜잭션 안에서 동기 실행된다. 잠금 순서(신청 → 명부)를 지키려고 신청을 먼저 지운다.
 * 승인된 신청이 남으면 회원과 명부를 다시 이을 수 있어 모두 지운다.
 */
@Component
public class MembershipWithdrawalListener {

	private final MembershipRequestRepository membershipRequestRepository;
	private final RosterMemberRepository rosterMemberRepository;

	public MembershipWithdrawalListener(MembershipRequestRepository membershipRequestRepository,
			RosterMemberRepository rosterMemberRepository) {
		this.membershipRequestRepository = membershipRequestRepository;
		this.rosterMemberRepository = rosterMemberRepository;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		membershipRequestRepository.deleteAllByUserId(event.userId());
		rosterMemberRepository.unlinkUser(event.userId());
	}
}
