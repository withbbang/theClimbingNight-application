package com.theclimbingnight.meetup.service;

/** 벙을 새로 개설했을 때 트랜잭션 안에서 발행한다 (복제도 개설을 거친다). 푸시 알림이 커밋 뒤에 구독한다. */
public record MeetupCreatedEvent(long meetupId, long hostUserId) {
}
