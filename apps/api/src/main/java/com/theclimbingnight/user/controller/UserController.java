package com.theclimbingnight.user.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.user.dto.NicknameUpdateRequest;
import com.theclimbingnight.user.dto.UserResponse;
import com.theclimbingnight.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ApiResponse<UserResponse> getMe(@CurrentUser AuthenticatedUser user) {
		return ApiResponse.success(userService.getMe(user.id()));
	}

	@PatchMapping
	public ApiResponse<UserResponse> changeNickname(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody NicknameUpdateRequest request) {
		return ApiResponse.success(userService.changeNickname(user.id(), request.nickname()));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@CurrentUser AuthenticatedUser user) {
		userService.withdraw(user.id());
	}

}
