package com.theclimbingnight.profile.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.profile.dto.BioUpdateRequest;
import com.theclimbingnight.profile.dto.GridPostResponse;
import com.theclimbingnight.profile.dto.ProfileResponse;
import com.theclimbingnight.profile.service.BioService;
import com.theclimbingnight.profile.service.ProfileQueryService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

	private final ProfileQueryService queryService;
	private final BioService bioService;

	public ProfileController(ProfileQueryService queryService, BioService bioService) {
		this.queryService = queryService;
		this.bioService = bioService;
	}

	@RequireRole(Role.USER)
	@GetMapping("/{id:\\d+}/profile")
	public ApiResponse<ProfileResponse> profile(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(queryService.profile(user.id(), id));
	}

	@RequireRole(Role.USER)
	@GetMapping("/{id:\\d+}/posts")
	public ApiResponse<CursorResponse<GridPostResponse>> posts(@CurrentUser AuthenticatedUser user,
			@PathVariable long id, @RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "18") @Min(1) @Max(30) int size) {
		return ApiResponse.success(queryService.posts(user.id(), id, cursor, size));
	}

	@RequireRole(Role.USER)
	@PatchMapping("/me/profile")
	public ApiResponse<ProfileResponse> updateBio(@CurrentUser AuthenticatedUser user,
			@RequestBody BioUpdateRequest request) {
		return ApiResponse.success(bioService.update(user.id(), request.bio()));
	}
}
