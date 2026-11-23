package com.theclimbingnight.membership.service;

import com.theclimbingnight.membership.entity.MembershipRequestStatus;

/** 운영진이 신청을 승인·거절했을 때 발행한다. 푸시 알림(계획 4)이 구독한다. */
public record MembershipReviewedEvent(long userId, MembershipRequestStatus status) {
}
