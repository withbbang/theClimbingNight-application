package com.theclimbingnight.setting.controller;

import com.theclimbingnight.auth.web.PublicApi;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.setting.dto.AppSettingsResponse;
import com.theclimbingnight.setting.dto.AppSettingsUpdateRequest;
import com.theclimbingnight.setting.service.AppSettingService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app-settings")
public class AppSettingController {

	private final AppSettingService appSettingService;

	public AppSettingController(AppSettingService appSettingService) {
		this.appSettingService = appSettingService;
	}

	@PublicApi
	@GetMapping
	public ApiResponse<AppSettingsResponse> get() {
		return ApiResponse.success(appSettingService.get());
	}

	@RequireRole(Role.ADMIN)
	@PutMapping
	public ApiResponse<AppSettingsResponse> update(@Valid @RequestBody AppSettingsUpdateRequest request) {
		return ApiResponse.success(appSettingService.update(request));
	}
}
