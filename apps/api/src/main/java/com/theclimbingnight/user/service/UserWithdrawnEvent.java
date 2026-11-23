package com.theclimbingnight.user.service;

/** 탈퇴 트랜잭션 안에서 발행된다. 계획 2의 명부 연결 해제가 구독한다. */
public record UserWithdrawnEvent(long userId) {
}
