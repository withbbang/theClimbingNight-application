package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MembershipWithdrawalListenerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock MembershipRequestRepository membershipRequestRepository;
	@Mock RosterMemberRepository rosterMemberRepository;

	@Test
	void 탈퇴하면_대기_신청을_거절하고_명부_연결을_끊는다() {
		// given
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", 7L);
		MembershipRequest request = MembershipRequest.create(user, "철수");
		given(membershipRequestRepository.findAllByUserIdAndStatus(7L, MembershipRequestStatus.PENDING))
				.willReturn(List.of(request));
		MembershipWithdrawalListener listener = new MembershipWithdrawalListener(membershipRequestRepository,
				rosterMemberRepository, new MutableClock(NOW));

		// when
		listener.on(new UserWithdrawnEvent(7L));

		// then
		assertThat(request.getStatus()).isEqualTo(MembershipRequestStatus.REJECTED);
		assertThat(request.getRejectReason()).isEqualTo("회원 탈퇴로 신청이 종료되었습니다.");
		assertThat(request.getReviewedBy()).isNull();
		assertThat(request.getReviewedAt()).isEqualTo(NOW);
		InOrder order = inOrder(membershipRequestRepository, rosterMemberRepository);
		order.verify(membershipRequestRepository).findAllByUserIdAndStatus(7L, MembershipRequestStatus.PENDING);
		order.verify(rosterMemberRepository).unlinkUser(7L);
	}
}
