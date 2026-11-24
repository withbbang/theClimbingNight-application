package com.theclimbingnight.membership.service;

/** 동호회원 자격 해제(MEMBER → USER) 트랜잭션 안에서 발행된다. 벙 신청 취소가 구독한다. */
public record MemberRevokedEvent(long userId) {
}
