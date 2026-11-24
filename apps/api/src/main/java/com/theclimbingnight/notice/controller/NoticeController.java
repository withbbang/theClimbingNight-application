package com.theclimbingnight.notice.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.PublicApi;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.notice.dto.NoticeRequest;
import com.theclimbingnight.notice.dto.NoticeResponse;
import com.theclimbingnight.notice.dto.NoticeSummaryResponse;
import com.theclimbingnight.notice.service.NoticeService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/notices")
public class NoticeController {

	private final NoticeService noticeService;

	public NoticeController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	@PublicApi
	@GetMapping
	public ApiResponse<PageResponse<NoticeSummaryResponse>> list(
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(noticeService.list(page, size));
	}

	@PublicApi
	@GetMapping("/{id}")
	public ApiResponse<NoticeResponse> get(@PathVariable long id) {
		return ApiResponse.success(noticeService.get(id));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<NoticeResponse> create(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody NoticeRequest request) {
		return ApiResponse.success(noticeService.create(user.id(), request));
	}

	@RequireRole(Role.ADMIN)
	@PutMapping("/{id}")
	public ApiResponse<NoticeResponse> update(@PathVariable long id, @Valid @RequestBody NoticeRequest request) {
		return ApiResponse.success(noticeService.update(id, request));
	}

	@RequireRole(Role.ADMIN)
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		noticeService.delete(id);
	}
}
