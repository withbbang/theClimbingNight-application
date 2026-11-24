package com.theclimbingnight.penalty.dto;

/** 명부별 활성 블랙리스트 횟수. recentCount는 벙 시작 시각이 최근 3개월(서울 기준) 안인 기록 수. */
public record PenaltySummaryResponse(long rosterMemberId, String somoimNickname, long totalCount, long recentCount) {
}
