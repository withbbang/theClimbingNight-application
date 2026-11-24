package com.theclimbingnight.push.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.push.dto.NotificationSettingRequest;
import com.theclimbingnight.push.dto.NotificationSettingResponse;
import com.theclimbingnight.push.service.NotificationSettingService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-settings")
public class NotificationSettingController {

	private final NotificationSettingService notificationSettingService;

	public NotificationSettingController(NotificationSettingService notificationSettingService) {
		this.notificationSettingService = notificationSettingService;
	}

	@RequireRole(Role.USER)
	@GetMapping("/me")
	public ApiResponse<NotificationSettingResponse> getMine(@CurrentUser AuthenticatedUser user) {
		return ApiResponse.success(notificationSettingService.getMine(user.id()));
	}

	@RequireRole(Role.USER)
	@PutMapping("/me")
	public ApiResponse<NotificationSettingResponse> update(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody NotificationSettingRequest request) {
		return ApiResponse.success(notificationSettingService.update(user.id(), request));
	}
}
