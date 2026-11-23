package com.theclimbingnight.membership.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.membership.dto.MembershipApplyRequest;
import com.theclimbingnight.membership.dto.MembershipApproveRequest;
import com.theclimbingnight.membership.dto.MembershipRejectRequest;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.dto.MembershipReviewItemResponse;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.service.MembershipReviewService;
import com.theclimbingnight.membership.service.MembershipService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/membership-requests")
public class MembershipRequestController {

	private final MembershipService membershipService;
	private final MembershipReviewService membershipReviewService;

	public MembershipRequestController(MembershipService membershipService,
			MembershipReviewService membershipReviewService) {
		this.membershipService = membershipService;
		this.membershipReviewService = membershipReviewService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MembershipRequestResponse> apply(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody MembershipApplyRequest request) {
		return ApiResponse.success(membershipService.apply(user.id(), request.somoimNickname()));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MembershipRequestResponse>> getMine(@CurrentUser AuthenticatedUser user) {
		return membershipService.getMine(user.id())
				.map(response -> ResponseEntity.ok(ApiResponse.success(response)))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@RequireRole(Role.ADMIN)
	@GetMapping
	public ApiResponse<PageResponse<MembershipReviewItemResponse>> list(
			@RequestParam(defaultValue = "PENDING") MembershipRequestStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(membershipReviewService.list(status, page, size));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/{id}/approve")
	public ApiResponse<MembershipRequestResponse> approve(@CurrentUser AuthenticatedUser user,
			@PathVariable long id, @Valid @RequestBody MembershipApproveRequest request) {
		return ApiResponse.success(membershipReviewService.approve(id, user.id(), request));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/{id}/reject")
	public ApiResponse<MembershipRequestResponse> reject(@CurrentUser AuthenticatedUser user,
			@PathVariable long id, @Valid @RequestBody MembershipRejectRequest request) {
		return ApiResponse.success(membershipReviewService.reject(id, user.id(), request.reason()));
	}
}
