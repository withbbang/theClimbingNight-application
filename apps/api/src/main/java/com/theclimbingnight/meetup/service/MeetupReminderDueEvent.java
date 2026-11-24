package com.theclimbingnight.meetup.service;

import java.util.List;

/** 벙 일정 알림 대상이 정해졌을 때 트랜잭션 안에서 발행한다. 확정자의 명부 ID를 담는다. */
public record MeetupReminderDueEvent(long meetupId, List<Long> rosterMemberIds) {
}
