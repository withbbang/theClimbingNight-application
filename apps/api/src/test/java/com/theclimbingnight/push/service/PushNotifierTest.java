package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.inquiry.service.InquiryAnsweredEvent;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.meetup.service.MeetupApplicationPromotedEvent;
import com.theclimbingnight.meetup.service.MeetupCanceledEvent;
import com.theclimbingnight.meetup.service.MeetupCreatedEvent;
import com.theclimbingnight.meetup.service.MeetupReminderDueEvent;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.service.MembershipReviewedEvent;
import com.theclimbingnight.notice.service.NoticePublishedEvent;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PushNotifierTest {

	/** 2030-03-02 10:30 KST (토요일). */
	private static final Instant STARTS = Instant.parse("2030-03-02T01:30:00Z");
	private static final String WHEN = "3월 2일(토) 10:30";
	private static final long MEETUP_ID = 10L;
	private static final List<String> TOKENS = List.of("ExponentPushToken[a]", "ExponentPushToken[b]");

	@Mock PushSender sender;
	@Mock PushTokenRepository tokenRepository;
	@Mock RosterMemberRepository rosterMemberRepository;
	@Mock MeetupRepository meetupRepository;
	@Captor ArgumentCaptor<Collection<String>> tokens;
	@Captor ArgumentCaptor<Map<String, Object>> data;
	PushNotifier notifier;

	@BeforeEach
	void setUp() {
		notifier = new PushNotifier(sender, tokenRepository, rosterMemberRepository, meetupRepository,
				mock(PlatformTransactionManager.class));
	}

	private Meetup meetup() {
		Meetup meetup = Meetup.create(new MeetupSpec("정기벙", null, "더클라임", STARTS, STARTS.plusSeconds(7200), 10, 12, false),
				User.create("host"));
		ReflectionTestUtils.setField(meetup, "id", MEETUP_ID);
		return meetup;
	}

	private void sentWith(String title, String body, Map<String, Object> expectedData) {
		verify(sender).send(tokens.capture(), eq(title), eq(body), data.capture());
		assertThat(tokens.getValue()).containsExactlyElementsOf(TOKENS);
		assertThat(data.getValue()).isEqualTo(expectedData);
	}

	@Test
	void 이벤트별_문구와_대상_인증_승인() {
		// given
		given(tokenRepository.findTokensByUserIds(List.of(5L))).willReturn(TOKENS);

		// when
		notifier.membershipReviewed(new MembershipReviewedEvent(5L, MembershipRequestStatus.APPROVED));

		// then
		sentWith("동호회원 인증 완료", "이제 벙에 신청할 수 있어요.", Map.of("type", "MEMBERSHIP_APPROVED"));
	}

	@Test
	void 이벤트별_문구와_대상_인증_거절() {
		// given
		given(tokenRepository.findTokensByUserIds(List.of(5L))).willReturn(TOKENS);

		// when
		notifier.membershipReviewed(new MembershipReviewedEvent(5L, MembershipRequestStatus.REJECTED));

		// then
		sentWith("동호회원 인증 결과", "인증 신청이 거절되었어요. 사유를 확인해 주세요.", Map.of("type", "MEMBERSHIP_REJECTED"));
	}

	@Test
	void 이벤트별_문구와_대상_승격() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup()));
		given(rosterMemberRepository.findLinkedUserIds(List.of(3L))).willReturn(List.of(5L));
		given(tokenRepository.findTokensByUserIds(List.of(5L))).willReturn(TOKENS);

		// when
		notifier.meetupPromoted(new MeetupApplicationPromotedEvent(MEETUP_ID, 3L));

		// then
		sentWith("벙 참여 확정", "정기벙 대기에서 확정되었어요. " + WHEN, Map.of("type", "MEETUP_PROMOTED", "meetupId", MEETUP_ID));
	}

	@Test
	void 이벤트별_문구와_대상_벙_취소() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup()));
		given(rosterMemberRepository.findLinkedUserIds(List.of(3L, 4L))).willReturn(List.of(5L, 6L));
		given(tokenRepository.findTokensByUserIds(List.of(5L, 6L))).willReturn(TOKENS);

		// when
		notifier.meetupCanceled(new MeetupCanceledEvent(MEETUP_ID, List.of(3L, 4L)));

		// then
		sentWith("벙 취소", "정기벙 벙이 취소되었어요. " + WHEN, Map.of("type", "MEETUP_CANCELED", "meetupId", MEETUP_ID));
	}

	@Test
	void 이벤트별_문구와_대상_새_벙() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup()));
		given(tokenRepository.findTokensForNewMeetup(1L)).willReturn(TOKENS);

		// when
		notifier.meetupCreated(new MeetupCreatedEvent(MEETUP_ID, 1L));

		// then
		sentWith("새 벙이 열렸어요", "정기벙 · " + WHEN, Map.of("type", "MEETUP_CREATED", "meetupId", MEETUP_ID));
	}

	@Test
	void 이벤트별_문구와_대상_새_공지() {
		// given
		given(tokenRepository.findTokensForNewNotice(1L)).willReturn(TOKENS);

		// when
		notifier.noticePublished(new NoticePublishedEvent(7L, 1L, "휴관 안내"));

		// then
		sentWith("새 공지", "휴관 안내", Map.of("type", "NOTICE_PUBLISHED", "noticeId", 7L));
	}

	@Test
	void 이벤트별_문구와_대상_문의_답변() {
		// given
		given(tokenRepository.findTokensByUserIds(List.of(5L))).willReturn(TOKENS);

		// when
		notifier.inquiryAnswered(new InquiryAnsweredEvent(9L, 5L));

		// then
		sentWith("문의 답변 도착", "문의하신 내용에 답변이 등록되었어요.", Map.of("type", "INQUIRY_ANSWERED", "inquiryId", 9L));
	}

	@Test
	void 이벤트별_문구와_대상_리마인드() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup()));
		given(rosterMemberRepository.findLinkedUserIds(List.of(3L))).willReturn(List.of(5L));
		given(tokenRepository.findTokensByUserIds(List.of(5L))).willReturn(TOKENS);

		// when
		notifier.meetupReminder(new MeetupReminderDueEvent(MEETUP_ID, List.of(3L)));

		// then
		sentWith("벙 일정 알림", "정기벙 · " + WHEN, Map.of("type", "MEETUP_REMINDER", "meetupId", MEETUP_ID));
	}

	@Test
	void 명부_연결이_없으면_건너뛴다() {
		// given
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(meetup()));
		given(rosterMemberRepository.findLinkedUserIds(List.of(3L))).willReturn(List.of());

		// when
		notifier.meetupCanceled(new MeetupCanceledEvent(MEETUP_ID, List.of(3L)));

		// then
		verify(tokenRepository, never()).findTokensByUserIds(anyCollection());
		verify(sender, never()).send(anyCollection(), anyString(), anyString(), anyMap());
	}

	@Test
	void 토큰이_없으면_발송하지_않는다() {
		// given
		given(tokenRepository.findTokensByUserIds(List.of(5L))).willReturn(List.of());

		// when
		notifier.inquiryAnswered(new InquiryAnsweredEvent(9L, 5L));

		// then
		verify(sender, never()).send(anyCollection(), anyString(), anyString(), anyMap());
	}

	@Test
	void 그사이_취소된_벙은_새_벙_알림을_보내지_않는다() {
		// given
		Meetup canceled = meetup();
		canceled.cancel(STARTS);
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(canceled));

		// when
		notifier.meetupCreated(new MeetupCreatedEvent(MEETUP_ID, 1L));

		// then
		verify(tokenRepository, never()).findTokensForNewMeetup(anyLong());
		verify(sender, never()).send(anyCollection(), anyString(), anyString(), anyMap());
	}

	@Test
	void 그사이_취소된_벙은_승격_알림을_보내지_않는다() {
		// given
		Meetup canceled = meetup();
		canceled.cancel(STARTS);
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(canceled));

		// when
		notifier.meetupPromoted(new MeetupApplicationPromotedEvent(MEETUP_ID, 3L));

		// then
		verify(sender, never()).send(anyCollection(), anyString(), anyString(), anyMap());
	}

	@Test
	void 그사이_취소된_벙은_리마인드를_보내지_않는다() {
		// given
		Meetup canceled = meetup();
		canceled.cancel(STARTS);
		given(meetupRepository.findById(MEETUP_ID)).willReturn(Optional.of(canceled));

		// when
		notifier.meetupReminder(new MeetupReminderDueEvent(MEETUP_ID, List.of(3L)));

		// then
		verify(sender, never()).send(anyCollection(), anyString(), anyString(), anyMap());
	}
}
