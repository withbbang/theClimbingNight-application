package com.theclimbingnight.attendance.dto;

import com.theclimbingnight.attendance.entity.AttendanceResult;
import jakarta.validation.constraints.NotNull;

public record AttendanceCorrectRequest(@NotNull AttendanceResult result) {
}
