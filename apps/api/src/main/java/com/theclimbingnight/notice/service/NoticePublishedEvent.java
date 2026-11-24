package com.theclimbingnight.notice.service;

/** 공지를 새로 작성했을 때 트랜잭션 안에서 발행한다. 수정에는 발행하지 않는다. */
public record NoticePublishedEvent(long noticeId, long authorId, String title) {
}
