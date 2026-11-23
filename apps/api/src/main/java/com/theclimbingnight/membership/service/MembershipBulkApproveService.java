package com.theclimbingnight.membership.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.membership.dto.BulkApproveResponse;
import com.theclimbingnight.membership.dto.MembershipApproveRequest;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 대기 신청 중 후보가 하나로 정해진 건을 한꺼번에 승인한다.
 * 클래스에 {@code @Transactional}을 붙이지 않는다. 건마다 {@link MembershipReviewService#approve}가
 * 자기 트랜잭션으로 돌아야 한 건이 실패해도 앞서 승인한 건이 롤백되지 않는다.
 */
@Service
public class MembershipBulkApproveService {

	private final MembershipRequestRepository membershipRequestRepository;
	private final CandidateMatcher candidateMatcher;
	private final MembershipReviewService membershipReviewService;

	public MembershipBulkApproveService(MembershipRequestRepository membershipRequestRepository,
			CandidateMatcher candidateMatcher, MembershipReviewService membershipReviewService) {
		this.membershipRequestRepository = membershipRequestRepository;
		this.candidateMatcher = candidateMatcher;
		this.membershipReviewService = membershipReviewService;
	}

	/** 그사이 다른 운영진이 처리했거나 명부가 이미 연결된 건(BusinessException)은 건너뛴다. */
	public BulkApproveResponse approveAll(long reviewerId) {
		List<MembershipRequest> pending = membershipRequestRepository
				.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING);
		Map<Long, List<RosterMember>> candidates = candidateMatcher.candidatesOf(pending);
		Set<Long> targetIds = candidateMatcher.bulkApprovableIds(candidates);

		int approved = 0;
		int skipped = 0;
		for (MembershipRequest request : pending) {
			Long requestId = request.getId();
			if (!targetIds.contains(requestId)) {
				continue;
			}
			Long rosterMemberId = candidates.get(requestId).get(0).getId();
			try {
				membershipReviewService.approve(requestId, reviewerId,
						new MembershipApproveRequest(rosterMemberId, false));
				approved++;
			} catch (BusinessException e) {
				skipped++;
			}
		}
		return new BulkApproveResponse(approved, skipped);
	}
}
