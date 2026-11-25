package com.theclimbingnight.membership.service;

import static org.mockito.Mockito.inOrder;

import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipWithdrawalListenerTest {

	@Mock MembershipRequestRepository membershipRequestRepository;
	@Mock RosterMemberRepository rosterMemberRepository;
	@InjectMocks MembershipWithdrawalListener listener;

	@Test
	void 탈퇴하면_신청을_지운_뒤_명부_연결을_끊는다() {
		// when
		listener.on(new UserWithdrawnEvent(7L));

		// then
		InOrder order = inOrder(membershipRequestRepository, rosterMemberRepository);
		order.verify(membershipRequestRepository).deleteAllByUserId(7L);
		order.verify(rosterMemberRepository).unlinkUser(7L);
	}
}
