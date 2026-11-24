package com.theclimbingnight.meetup.dto;

import java.util.List;

/** participants는 명단 비공개 벙을 동호회원이 볼 때 null. my는 명부가 없거나 신청 이력이 없으면 null. */
public record MeetupDetailResponse(MeetupResponse meetup, long confirmedCount, long waitlistCount,
		MyApplicationResponse my, List<ParticipantResponse> participants) {
}
