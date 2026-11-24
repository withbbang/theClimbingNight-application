package com.theclimbingnight.meetup.service;

import java.util.List;

/** 벙을 취소했을 때 발행한다. 취소 시점의 확정·대기 명부 ID를 담는다. 푸시 알림(계획 4)이 구독한다. */
public record MeetupCanceledEvent(long meetupId, List<Long> rosterMemberIds) {
}
