package com.theclimbingnight.attendance.controller;

import com.theclimbingnight.attendance.dto.AttendanceRateResponse;
import com.theclimbingnight.attendance.service.AttendancePeriod;
import com.theclimbingnight.attendance.service.AttendanceRateService;
import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.user.entity.Role;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceRateController {

	private final AttendanceRateService attendanceRateService;

	public AttendanceRateController(AttendanceRateService attendanceRateService) {
		this.attendanceRateService = attendanceRateService;
	}

	@RequireRole(Role.MEMBER)
	@GetMapping("/api/v1/attendance-rates/me")
	public ApiResponse<AttendanceRateResponse> mine(@CurrentUser AuthenticatedUser user,
			@RequestParam AttendancePeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ApiResponse.success(attendanceRateService.mine(user.id(), period, date));
	}
}
