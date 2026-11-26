package com.theclimbingnight.moderation.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.moderation.dto.ReportCreateRequest;
import com.theclimbingnight.moderation.dto.ReportTargetActionRequest;
import com.theclimbingnight.moderation.dto.ReportTargetResponse;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.service.ModerationService;
import com.theclimbingnight.moderation.service.ReportService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

	private final ReportService reportService;
	private final ModerationService moderationService;

	public ReportController(ReportService reportService, ModerationService moderationService) {
		this.reportService = reportService;
		this.moderationService = moderationService;
	}

	@RequireRole(Role.USER)
	@PostMapping("/reports")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void report(@CurrentUser AuthenticatedUser user, @Valid @RequestBody ReportCreateRequest request) {
		reportService.submit(user.id(), request);
	}

	@RequireRole(Role.ADMIN)
	@GetMapping("/report-targets")
	public ApiResponse<PageResponse<ReportTargetResponse>> targets(
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(moderationService.list(page, size));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/report-targets/{targetType}/{targetId}/actions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void act(@CurrentUser AuthenticatedUser user, @PathVariable ReportTargetType targetType,
			@PathVariable long targetId, @Valid @RequestBody ReportTargetActionRequest request) {
		moderationService.act(user.id(), targetType, targetId, request.action());
	}
}
