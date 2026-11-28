package com.theclimbingnight.profile.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.profile.dto.ProfileImageStartRequest;
import com.theclimbingnight.profile.dto.ProfileImageStatusResponse;
import com.theclimbingnight.profile.dto.ProfileImageUploadResponse;
import com.theclimbingnight.profile.service.ProfileImageService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileImageController {

	private final ProfileImageService service;

	public ProfileImageController(ProfileImageService service) {
		this.service = service;
	}

	@RequireRole(Role.USER)
	@PostMapping("/profile-images")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ProfileImageUploadResponse> start(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody ProfileImageStartRequest request) {
		return ApiResponse.success(service.start(user.id(), request.byteSize()));
	}

	@RequireRole(Role.USER)
	@PostMapping("/profile-images/{id}/confirm")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ApiResponse<ProfileImageStatusResponse> confirm(@CurrentUser AuthenticatedUser user,
			@PathVariable long id) {
		return ApiResponse.success(service.confirm(user.id(), id));
	}

	@RequireRole(Role.USER)
	@GetMapping("/profile-images/{id}")
	public ApiResponse<ProfileImageStatusResponse> status(@CurrentUser AuthenticatedUser user,
			@PathVariable long id) {
		return ApiResponse.success(service.status(user.id(), id));
	}

	@RequireRole(Role.USER)
	@DeleteMapping("/profile-image")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reset(@CurrentUser AuthenticatedUser user) {
		service.reset(user.id());
	}
}
