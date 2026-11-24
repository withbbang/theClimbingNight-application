package com.theclimbingnight.meetup.service;

/** 대기자가 확정으로 승격됐을 때 발행한다. 푸시 알림(계획 4)이 구독한다. */
public record MeetupApplicationPromotedEvent(long meetupId, long rosterMemberId) {
}
