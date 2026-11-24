package com.theclimbingnight.attendance.dto;

import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.attendance.entity.AttendanceSource;

public record AttendanceResponse(long id, long meetupId, long rosterMemberId, AttendanceResult result,
		AttendanceSource source) {

	public static AttendanceResponse from(Attendance attendance) {
		return new AttendanceResponse(attendance.getId(), attendance.getMeetup().getId(),
				attendance.getRosterMember().getId(), attendance.getResult(), attendance.getSource());
	}
}
