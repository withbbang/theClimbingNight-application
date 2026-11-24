package com.theclimbingnight.attendance.controller;

import com.theclimbingnight.attendance.dto.AttendanceCorrectRequest;
import com.theclimbingnight.attendance.dto.AttendanceResponse;
import com.theclimbingnight.attendance.service.AttendanceService;
import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendances")
public class AttendanceController {

	private final AttendanceService attendanceService;

	public AttendanceController(AttendanceService attendanceService) {
		this.attendanceService = attendanceService;
	}

	@RequireRole(Role.ADMIN)
	@PutMapping("/{id}")
	public ApiResponse<AttendanceResponse> correct(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody AttendanceCorrectRequest request) {
		return ApiResponse.success(attendanceService.correct(id, user.id(), request.result()));
	}
}
