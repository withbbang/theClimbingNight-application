package com.theclimbingnight.meetup.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.meetup.dto.MeetupCopyRequest;
import com.theclimbingnight.meetup.dto.MeetupCreateRequest;
import com.theclimbingnight.meetup.dto.MeetupDetailResponse;
import com.theclimbingnight.meetup.dto.MeetupResponse;
import com.theclimbingnight.meetup.dto.MeetupSummaryResponse;
import com.theclimbingnight.meetup.dto.MeetupUpdateRequest;
import com.theclimbingnight.meetup.service.MeetupAdminService;
import com.theclimbingnight.meetup.service.MeetupQueryService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetups")
public class MeetupController {

	private final MeetupAdminService meetupAdminService;

	private final MeetupQueryService meetupQueryService;

	public MeetupController(MeetupAdminService meetupAdminService, MeetupQueryService meetupQueryService) {
		this.meetupAdminService = meetupAdminService;
		this.meetupQueryService = meetupQueryService;
	}

	@RequireRole(Role.MEMBER)
	@GetMapping
	public ApiResponse<List<MeetupSummaryResponse>> list(@CurrentUser AuthenticatedUser user,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.success(meetupQueryService.list(user.id(), from, to));
	}

	@RequireRole(Role.MEMBER)
	@GetMapping("/{id}")
	public ApiResponse<MeetupDetailResponse> get(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(meetupQueryService.get(id, user.id(), user.role()));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MeetupResponse> create(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody MeetupCreateRequest request) {
		return ApiResponse.success(meetupAdminService.create(user.id(), request.toSpec()));
	}

	@RequireRole(Role.ADMIN)
	@PutMapping("/{id}")
	public ApiResponse<MeetupResponse> update(@PathVariable long id,
			@Valid @RequestBody MeetupUpdateRequest request) {
		return ApiResponse.success(meetupAdminService.update(id, request.toSpec()));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/{id}/cancel")
	public ApiResponse<MeetupResponse> cancel(@PathVariable long id) {
		return ApiResponse.success(meetupAdminService.cancel(id));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/{id}/copy")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MeetupResponse> copy(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody MeetupCopyRequest request) {
		return ApiResponse.success(meetupAdminService.copy(id, user.id(), request.startsAt().toInstant(),
				request.endsAt().toInstant()));
	}
}
