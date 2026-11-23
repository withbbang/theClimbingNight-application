package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.membership.dto.BulkApproveResponse;
import com.theclimbingnight.membership.dto.MembershipApproveRequest;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.user.entity.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MembershipBulkApproveServiceTest {

	private static final long REVIEWER_ID = 1L;

	@Mock MembershipRequestRepository membershipRequestRepository;
	@Mock CandidateMatcher candidateMatcher;
	@Mock MembershipReviewService reviewService;

	private MembershipBulkApproveService service;

	@BeforeEach
	void setUp() {
		service = new MembershipBulkApproveService(membershipRequestRepository, candidateMatcher, reviewService);
	}

	@Test
	void 대상인_신청만_그_후보로_승인한다() {
		// given
		MembershipRequest r1 = request(1L);
		MembershipRequest r2 = request(2L);
		MembershipRequest r3 = request(3L);
		RosterMember m1 = roster(11L);
		RosterMember m3 = roster(13L);
		Map<Long, List<RosterMember>> candidates = new LinkedHashMap<>();
		candidates.put(1L, List.of(m1));
		candidates.put(2L, List.of());
		candidates.put(3L, List.of(m3));
		given(membershipRequestRepository.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING))
				.willReturn(List.of(r1, r2, r3));
		given(candidateMatcher.candidatesOf(List.of(r1, r2, r3))).willReturn(candidates);
		given(candidateMatcher.bulkApprovableIds(candidates)).willReturn(Set.of(1L, 3L));

		// when
		BulkApproveResponse response = service.approveAll(REVIEWER_ID);

		// then
		assertThat(response.approvedCount()).isEqualTo(2);
		assertThat(response.skippedCount()).isZero();
		verify(reviewService).approve(1L, REVIEWER_ID, new MembershipApproveRequest(11L, false));
		verify(reviewService).approve(3L, REVIEWER_ID, new MembershipApproveRequest(13L, false));
		verify(reviewService, never()).approve(org.mockito.ArgumentMatchers.eq(2L), anyLong(), any());
	}

	@Test
	void 승인_중_실패한_건은_건너뛰고_계속한다() {
		// given
		MembershipRequest r1 = request(1L);
		MembershipRequest r3 = request(3L);
		Map<Long, List<RosterMember>> candidates = new LinkedHashMap<>();
		candidates.put(1L, List.of(roster(11L)));
		candidates.put(3L, List.of(roster(13L)));
		given(membershipRequestRepository.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING))
				.willReturn(List.of(r1, r3));
		given(candidateMatcher.candidatesOf(List.of(r1, r3))).willReturn(candidates);
		given(candidateMatcher.bulkApprovableIds(candidates)).willReturn(Set.of(1L, 3L));
		doThrow(new BusinessException(ErrorCode.ROSTER_ALREADY_LINKED)).when(reviewService)
				.approve(1L, REVIEWER_ID, new MembershipApproveRequest(11L, false));

		// when
		BulkApproveResponse response = service.approveAll(REVIEWER_ID);

		// then
		assertThat(response.approvedCount()).isEqualTo(1);
		assertThat(response.skippedCount()).isEqualTo(1);
		verify(reviewService).approve(3L, REVIEWER_ID, new MembershipApproveRequest(13L, false));
	}

	@Test
	void 대기_신청이_없으면_0건이다() {
		// given
		given(membershipRequestRepository.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING))
				.willReturn(List.of());
		given(candidateMatcher.candidatesOf(List.of())).willReturn(Map.of());
		given(candidateMatcher.bulkApprovableIds(Map.of())).willReturn(Set.of());

		// when
		BulkApproveResponse response = service.approveAll(REVIEWER_ID);

		// then
		assertThat(response.approvedCount()).isZero();
		assertThat(response.skippedCount()).isZero();
		verify(reviewService, never()).approve(anyLong(), anyLong(), any());
	}

	private static MembershipRequest request(long id) {
		User user = User.create("user" + id);
		ReflectionTestUtils.setField(user, "id", id + 100);
		MembershipRequest request = MembershipRequest.create(user, "닉" + id);
		ReflectionTestUtils.setField(request, "id", id);
		return request;
	}

	private static RosterMember roster(long id) {
		RosterMember member = RosterMember.create("닉" + id, null, RosterSource.IMPORT);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
