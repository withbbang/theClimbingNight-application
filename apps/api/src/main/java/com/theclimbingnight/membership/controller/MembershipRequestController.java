package com.theclimbingnight.membership.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.membership.dto.MembershipApplyRequest;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/membership-requests")
public class MembershipRequestController {

	private final MembershipService membershipService;

	public MembershipRequestController(MembershipService membershipService) {
		this.membershipService = membershipService;
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
}
