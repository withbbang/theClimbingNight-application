package com.theclimbingnight.meetup.dto;

import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import java.time.Instant;

/**
 * 운영진 신청자 목록 한 줄. userId는 명부가 회원과 연결되지 않았으면(탈퇴 등) null, attendance*는 참석 기록이 없으면 null.
 * lateCancelPenalized는 이 벙의 활성 늦은 취소 블랙리스트가 있는지, penaltyTotal·penaltyRecent는 명부의 활성 블랙리스트 누적·최근 3개월 수.
 */
public record MeetupApplicationAdminResponse(long id, long rosterMemberId, String somoimNickname, Long userId,
		MeetupApplicationStatus status, Instant appliedAt, Instant confirmedAt, Instant canceledAt,
		boolean lateCancel, String cancelReason, Long attendanceId, AttendanceResult attendanceResult,
		boolean lateCancelPenalized, long penaltyTotal, long penaltyRecent) {
}
