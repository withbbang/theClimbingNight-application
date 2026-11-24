package com.theclimbingnight.penalty.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.penalty.dto.PenaltyResponse;
import com.theclimbingnight.penalty.dto.PenaltyRevokeRequest;
import com.theclimbingnight.penalty.dto.PenaltySummaryResponse;
import com.theclimbingnight.penalty.dto.RosterPenaltiesResponse;
import com.theclimbingnight.penalty.service.PenaltyService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 블랙리스트 조회·취소. 늦은 취소 기록은 MeetupApplicationController가 받는다. */
@RestController
public class PenaltyController {

	private final PenaltyService penaltyService;

	public PenaltyController(PenaltyService penaltyService) {
		this.penaltyService = penaltyService;
	}

	@RequireRole(Role.ADMIN)
	@GetMapping("/api/v1/penalties")
	public ApiResponse<PageResponse<PenaltySummaryResponse>> list(
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(penaltyService.summaries(page, size));
	}

	@RequireRole(Role.MEMBER)
	@GetMapping("/api/v1/penalties/me")
	public ApiResponse<List<PenaltyResponse>> mine(@CurrentUser AuthenticatedUser user) {
		return ApiResponse.success(penaltyService.mine(user.id()));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/api/v1/penalties/{id}/revoke")
	public ApiResponse<PenaltyResponse> revoke(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody PenaltyRevokeRequest request) {
		return ApiResponse.success(penaltyService.revoke(id, user.id(), request.reason()));
	}

	@RequireRole(Role.ADMIN)
	@GetMapping("/api/v1/roster-members/{id}/penalties")
	public ApiResponse<RosterPenaltiesResponse> ofRoster(@PathVariable long id) {
		return ApiResponse.success(penaltyService.ofRoster(id));
	}
}
