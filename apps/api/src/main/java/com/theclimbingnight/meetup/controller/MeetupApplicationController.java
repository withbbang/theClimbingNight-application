package com.theclimbingnight.meetup.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.meetup.dto.MeetupCancelApplicationRequest;
import com.theclimbingnight.meetup.dto.MyApplicationResponse;
import com.theclimbingnight.meetup.service.MeetupApplicationService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetupApplicationController {

	private final MeetupApplicationService meetupApplicationService;

	public MeetupApplicationController(MeetupApplicationService meetupApplicationService) {
		this.meetupApplicationService = meetupApplicationService;
	}

	@RequireRole(Role.MEMBER)
	@PostMapping("/api/v1/meetups/{id}/applications")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MyApplicationResponse> apply(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(meetupApplicationService.apply(id, user.id()));
	}

	/** 본문은 선택이다. 없으면 사유 없이 취소한다. */
	@RequireRole(Role.MEMBER)
	@PostMapping("/api/v1/meetups/{id}/applications/me/cancel")
	public ApiResponse<MyApplicationResponse> cancel(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody(required = false) MeetupCancelApplicationRequest request) {
		String reason = request == null ? null : request.reason();
		return ApiResponse.success(meetupApplicationService.cancel(id, user.id(), reason));
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/api/v1/meetup-applications/{id}/remove")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@PathVariable long id) {
		meetupApplicationService.remove(id);
	}
}
