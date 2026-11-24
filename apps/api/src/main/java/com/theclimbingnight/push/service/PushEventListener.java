package com.theclimbingnight.push.service;

import com.theclimbingnight.common.FailureSummary;
import com.theclimbingnight.inquiry.service.InquiryAnsweredEvent;
import com.theclimbingnight.meetup.service.MeetupApplicationPromotedEvent;
import com.theclimbingnight.meetup.service.MeetupCanceledEvent;
import com.theclimbingnight.meetup.service.MeetupCreatedEvent;
import com.theclimbingnight.meetup.service.MeetupReminderDueEvent;
import com.theclimbingnight.membership.service.MembershipReviewedEvent;
import com.theclimbingnight.notice.service.NoticePublishedEvent;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋이 끝난 이벤트만 받아 푸시 실행기로 넘긴다. 롤백된 이벤트는 오지 않는다. 푸시 실패는 본래 요청을 실패시키지 않는다.
 * <p>Hibernate의 SQL 오류 로그가 꺼져 있고 예외 메시지에 푸시 토큰이 들어갈 수 있어, 실패는 여기서 클래스 이름과 DB 오류 코드만 남긴다
 * (예외 객체를 로그에 넘기지 않는다). 큐가 차면 Spring의 {@code TaskRejectedException}이 오는데
 * {@code RejectedExecutionException}의 하위 타입이라 함께 잡힌다.
 */
@Component
public class PushEventListener {

	private static final Logger log = LoggerFactory.getLogger(PushEventListener.class);

	private final TaskExecutor pushExecutor;
	private final PushNotifier notifier;

	public PushEventListener(@Qualifier("pushExecutor") TaskExecutor pushExecutor, PushNotifier notifier) {
		this.pushExecutor = pushExecutor;
		this.notifier = notifier;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(MembershipReviewedEvent event) {
		dispatch("MembershipReviewed", () -> notifier.membershipReviewed(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(MeetupApplicationPromotedEvent event) {
		dispatch("MeetupApplicationPromoted", () -> notifier.meetupPromoted(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(MeetupCanceledEvent event) {
		dispatch("MeetupCanceled", () -> notifier.meetupCanceled(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(MeetupCreatedEvent event) {
		dispatch("MeetupCreated", () -> notifier.meetupCreated(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(NoticePublishedEvent event) {
		dispatch("NoticePublished", () -> notifier.noticePublished(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(InquiryAnsweredEvent event) {
		dispatch("InquiryAnswered", () -> notifier.inquiryAnswered(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(MeetupReminderDueEvent event) {
		dispatch("MeetupReminderDue", () -> notifier.meetupReminder(event));
	}

	private void dispatch(String eventName, Runnable task) {
		try {
			pushExecutor.execute(() -> {
				try {
					task.run();
				} catch (RuntimeException e) {
					log.error("푸시 알림 처리 실패 event={} type={}", eventName, FailureSummary.of(e));
				}
			});
		} catch (RejectedExecutionException e) {
			log.warn("푸시 큐가 가득 차 알림을 건너뜀 event={}", eventName);
		} catch (RuntimeException e) {
			log.error("푸시 알림 접수 실패 event={} type={}", eventName, FailureSummary.of(e));
		}
	}
}
