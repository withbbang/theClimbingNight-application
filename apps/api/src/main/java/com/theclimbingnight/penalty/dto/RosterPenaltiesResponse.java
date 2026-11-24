package com.theclimbingnight.penalty.dto;

import java.util.List;

/** totalCount·recentCount는 활성 기록만 센다. penalties에는 취소된 기록도 들어간다 (최신순). */
public record RosterPenaltiesResponse(long rosterMemberId, String somoimNickname, long totalCount, long recentCount,
		List<PenaltyResponse> penalties) {
}
