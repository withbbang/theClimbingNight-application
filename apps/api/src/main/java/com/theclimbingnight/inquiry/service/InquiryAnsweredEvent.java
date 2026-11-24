package com.theclimbingnight.inquiry.service;

/** 문의에 처음 답변했을 때 트랜잭션 안에서 발행한다. 답변 수정에는 발행하지 않는다. */
public record InquiryAnsweredEvent(long inquiryId, long userId) {
}
