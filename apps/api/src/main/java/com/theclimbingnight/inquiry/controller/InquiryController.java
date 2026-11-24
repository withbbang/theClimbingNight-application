package com.theclimbingnight.inquiry.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.inquiry.dto.InquiryAdminResponse;
import com.theclimbingnight.inquiry.dto.InquiryAnswerRequest;
import com.theclimbingnight.inquiry.dto.InquiryCreateRequest;
import com.theclimbingnight.inquiry.dto.InquiryResponse;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import com.theclimbingnight.inquiry.service.InquiryService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {

	private final InquiryService inquiryService;

	public InquiryController(InquiryService inquiryService) {
		this.inquiryService = inquiryService;
	}

	@RequireRole(Role.USER)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<InquiryResponse> create(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody InquiryCreateRequest request) {
		return ApiResponse.success(inquiryService.create(user.id(), request));
	}

	@RequireRole(Role.USER)
	@GetMapping("/me")
	public ApiResponse<PageResponse<InquiryResponse>> mine(@CurrentUser AuthenticatedUser user,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(inquiryService.mine(user.id(), page, size));
	}

	@RequireRole(Role.USER)
	@GetMapping("/me/{id}")
	public ApiResponse<InquiryResponse> getMine(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(inquiryService.getMine(user.id(), id));
	}

	@RequireRole(Role.ADMIN)
	@GetMapping
	public ApiResponse<PageResponse<InquiryAdminResponse>> list(@RequestParam(required = false) InquiryStatus status,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(inquiryService.list(status, page, size));
	}

	@RequireRole(Role.ADMIN)
	@GetMapping("/{id}")
	public ApiResponse<InquiryAdminResponse> get(@PathVariable long id) {
		return ApiResponse.success(inquiryService.get(id));
	}

	@RequireRole(Role.ADMIN)
	@PutMapping("/{id}/answer")
	public ApiResponse<InquiryAdminResponse> answer(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody InquiryAnswerRequest request) {
		return ApiResponse.success(inquiryService.answer(id, user.id(), request));
	}
}
