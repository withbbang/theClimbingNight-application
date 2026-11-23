package com.theclimbingnight.membership.dto;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import java.time.Instant;

public record MembershipRequestResponse(long id, String somoimNickname, MembershipRequestStatus status,
		String rejectReason, Instant createdAt, Instant reviewedAt) {

	public static MembershipRequestResponse from(MembershipRequest request) {
		return new MembershipRequestResponse(request.getId(), request.getSomoimNickname(), request.getStatus(),
				request.getRejectReason(), request.getCreatedAt(), request.getReviewedAt());
	}
}
