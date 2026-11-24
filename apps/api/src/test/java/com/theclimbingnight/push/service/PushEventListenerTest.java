package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.theclimbingnight.inquiry.service.InquiryAnsweredEvent;
import com.theclimbingnight.meetup.service.MeetupApplicationPromotedEvent;
import com.theclimbingnight.meetup.service.MeetupCanceledEvent;
import com.theclimbingnight.meetup.service.MeetupCreatedEvent;
import com.theclimbingnight.meetup.service.MeetupReminderDueEvent;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.service.MembershipReviewedEvent;
import com.theclimbingnight.notice.service.NoticePublishedEvent;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PushEventListenerTest {

	private static final String SECRET = "ExponentPushToken[secret-token]";

	private final PushNotifier notifier = mock(PushNotifier.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
	private Logger logger;

	@BeforeEach
	void attachAppender() {
		logger = (Logger) org.slf4j.LoggerFactory.getLogger(PushEventListener.class);
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		logger.detachAppender(appender);
		appender.stop();
	}

	@Test
	void 이벤트마다_알리미를_호출한다() {
		// given
		PushEventListener listener = new PushEventListener(new SyncTaskExecutor(), notifier);
		MembershipReviewedEvent reviewed = new MembershipReviewedEvent(1L, MembershipRequestStatus.APPROVED);
		MeetupApplicationPromotedEvent promoted = new MeetupApplicationPromotedEvent(2L, 3L);
		MeetupCanceledEvent canceled = new MeetupCanceledEvent(2L, List.of(3L));
		MeetupCreatedEvent created = new MeetupCreatedEvent(2L, 1L);
		NoticePublishedEvent notice = new NoticePublishedEvent(4L, 1L, "공지");
		InquiryAnsweredEvent answered = new InquiryAnsweredEvent(5L, 6L);
		MeetupReminderDueEvent reminder = new MeetupReminderDueEvent(2L, List.of(3L));

		// when
		listener.on(reviewed);
		listener.on(promoted);
		listener.on(canceled);
		listener.on(created);
		listener.on(notice);
		listener.on(answered);
		listener.on(reminder);

		// then
		verify(notifier).membershipReviewed(reviewed);
		verify(notifier).meetupPromoted(promoted);
		verify(notifier).meetupCanceled(canceled);
		verify(notifier).meetupCreated(created);
		verify(notifier).noticePublished(notice);
		verify(notifier).inquiryAnswered(answered);
		verify(notifier).meetupReminder(reminder);
	}

	@Test
	void 큐가_차도_예외를_던지지_않는다() throws Exception {
		// given: 스레드 1개, 큐 없음. 첫 작업이 스레드를 붙잡고 있어 다음 제출은 TaskRejectedException이다.
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(0);
		executor.initialize();
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(1);
		executor.execute(() -> {
			started.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		assertThatCode(() -> executor.execute(() -> { })).isInstanceOf(TaskRejectedException.class)
				.isInstanceOf(RejectedExecutionException.class);
		PushEventListener listener = new PushEventListener(executor, notifier);

		try {
			// when, then
			assertThatCode(() -> listener.on(new MeetupCanceledEvent(1L, List.of(2L)))).doesNotThrowAnyException();
			verify(notifier, never()).meetupCanceled(any());
			assertThat(appender.list).anySatisfy(e -> {
				assertThat(e.getLevel()).isEqualTo(Level.WARN);
				assertThat(e.getThrowableProxy()).isNull();
			});
		} finally {
			release.countDown();
			executor.shutdown();
		}
	}

	@Test
	void 발송_실패가_밖으로_새지_않는다() {
		// given
		willThrow(new IllegalStateException("push failed for " + SECRET)).given(notifier).noticePublished(any());
		PushEventListener listener = new PushEventListener(new SyncTaskExecutor(), notifier);

		// when, then
		assertThatCode(() -> listener.on(new NoticePublishedEvent(1L, 2L, "공지"))).doesNotThrowAnyException();
		assertThat(appender.list).anySatisfy(e -> {
			assertThat(e.getLevel()).isEqualTo(Level.ERROR);
			assertThat(e.getThrowableProxy()).isNull();
		});
		assertThat(appender.list).noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains("secret"));
	}
}
