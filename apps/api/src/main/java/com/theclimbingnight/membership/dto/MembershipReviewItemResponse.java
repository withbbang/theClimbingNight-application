package com.theclimbingnight.membership.dto;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.user.dto.UserResponse;
import java.time.Instant;
import java.util.List;

public record MembershipReviewItemResponse(long id, UserResponse user, String somoimNickname,
		MembershipRequestStatus status, String rejectReason, Instant createdAt,
		List<RosterCandidateResponse> candidates, boolean bulkApprovable) {

	public static MembershipReviewItemResponse of(MembershipRequest request, List<RosterCandidateResponse> candidates,
			boolean bulkApprovable) {
		return new MembershipReviewItemResponse(request.getId(), UserResponse.from(request.getUser()),
				request.getSomoimNickname(), request.getStatus(), request.getRejectReason(), request.getCreatedAt(),
				candidates, bulkApprovable);
	}
}
