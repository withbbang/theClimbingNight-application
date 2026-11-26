package com.theclimbingnight.push.service;

import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.inquiry.service.InquiryAnsweredEvent;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.meetup.service.MeetupApplicationPromotedEvent;
import com.theclimbingnight.meetup.service.MeetupCanceledEvent;
import com.theclimbingnight.meetup.service.MeetupCreatedEvent;
import com.theclimbingnight.meetup.service.MeetupReminderDueEvent;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.service.MembershipReviewedEvent;
import com.theclimbingnight.moderation.service.ReportSubmittedEvent;
import com.theclimbingnight.notice.service.NoticePublishedEvent;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이벤트마다 받는 사람과 문구를 정해 {@link PushSender}로 보낸다. 커밋 뒤 푸시 스레드(또는 테스트의 동기 실행기)에서 불린다.
 * <p>조회는 리포지토리만 쓰고 LAZY 연관을 건드리지 않는다. 조회마다 새 읽기 전용 트랜잭션(REQUIRES_NEW)에서 하고 끝낸 뒤에 발송한다.
 * 커밋 직후에는 원래 트랜잭션의 자원이 아직 묶여 있어 그대로 읽으면 그 낡은 영속성 컨텍스트에 참여하고, 발송 중 연결을 붙들게 된다.
 */
@Component
public class PushNotifier {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN)
			.withZone(TimeZones.KST);

	private final PushSender sender;
	private final PushTokenRepository tokenRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final MeetupRepository meetupRepository;
	private final TransactionTemplate readTransaction;

	public PushNotifier(PushSender sender, PushTokenRepository tokenRepository,
			RosterMemberRepository rosterMemberRepository, MeetupRepository meetupRepository,
			PlatformTransactionManager transactionManager) {
		this.sender = sender;
		this.tokenRepository = tokenRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.meetupRepository = meetupRepository;
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.readTransaction.setReadOnly(true);
	}

	/** 보낼 내용. 토큰이 비면 보내지 않는다. */
	private record Plan(List<String> tokens, String body) {
	}

	public void membershipReviewed(MembershipReviewedEvent event) {
		if (event.status() == MembershipRequestStatus.APPROVED) {
			deliver(read(() -> new Plan(tokensOfUsers(List.of(event.userId())), "이제 벙에 신청할 수 있어요.")),
					"동호회원 인증 완료", Map.of("type", "MEMBERSHIP_APPROVED"));
		} else if (event.status() == MembershipRequestStatus.REJECTED) {
			deliver(read(() -> new Plan(tokensOfUsers(List.of(event.userId())),
					"인증 신청이 거절되었어요. 사유를 확인해 주세요.")), "동호회원 인증 결과", Map.of("type", "MEMBERSHIP_REJECTED"));
		}
	}

	public void meetupPromoted(MeetupApplicationPromotedEvent event) {
		deliver(openMeetupPlan(event.meetupId(), List.of(event.rosterMemberId()),
				meetup -> meetup.getTitle() + " 대기에서 확정되었어요. " + when(meetup)), "벙 참여 확정",
				meetupData("MEETUP_PROMOTED", event.meetupId()));
	}

	/** 취소 알림은 취소된 벙이어야 의미가 있으므로 상태를 보지 않는다. */
	public void meetupCanceled(MeetupCanceledEvent event) {
		deliver(read(() -> meetupRepository.findById(event.meetupId())
				.map(meetup -> new Plan(tokensOfRoster(event.rosterMemberIds()),
						meetup.getTitle() + " 벙이 취소되었어요. " + when(meetup)))
				.orElse(null)), "벙 취소", meetupData("MEETUP_CANCELED", event.meetupId()));
	}

	public void meetupCreated(MeetupCreatedEvent event) {
		deliver(read(() -> meetupRepository.findById(event.meetupId()).filter(Meetup::isOpen)
				.map(meetup -> new Plan(tokenRepository.findTokensForNewMeetup(event.hostUserId()),
						meetup.getTitle() + " · " + when(meetup)))
				.orElse(null)), "새 벙이 열렸어요", meetupData("MEETUP_CREATED", event.meetupId()));
	}

	public void noticePublished(NoticePublishedEvent event) {
		deliver(read(() -> new Plan(tokenRepository.findTokensForNewNotice(event.authorId()), event.title())),
				"새 공지", Map.of("type", "NOTICE_PUBLISHED", "noticeId", event.noticeId()));
	}

	public void inquiryAnswered(InquiryAnsweredEvent event) {
		deliver(read(() -> new Plan(tokensOfUsers(List.of(event.userId())), "문의하신 내용에 답변이 등록되었어요.")),
				"문의 답변 도착", Map.of("type", "INQUIRY_ANSWERED", "inquiryId", event.inquiryId()));
	}

	public void meetupReminder(MeetupReminderDueEvent event) {
		deliver(openMeetupPlan(event.meetupId(), event.rosterMemberIds(),
				meetup -> meetup.getTitle() + " · " + when(meetup)), "벙 일정 알림",
				meetupData("MEETUP_REMINDER", event.meetupId()));
	}

	/** 대상의 첫 신고만 온다. 신고한 사람이 운영진이면 그 사람은 뺀다. */
	public void reportSubmitted(ReportSubmittedEvent event) {
		deliver(read(() -> new Plan(tokenRepository.findTokensOfAdmins(event.reporterId()), "검토할 신고가 들어왔어요.")),
				"새 신고", Map.of("type", "REPORT_SUBMITTED"));
	}

	/** 그사이 취소된 벙이면 계획이 없다. */
	private Plan openMeetupPlan(long meetupId, Collection<Long> rosterMemberIds, Function<Meetup, String> body) {
		return read(() -> meetupRepository.findById(meetupId).filter(Meetup::isOpen)
				.map(meetup -> new Plan(tokensOfRoster(rosterMemberIds), body.apply(meetup))).orElse(null));
	}

	private List<String> tokensOfRoster(Collection<Long> rosterMemberIds) {
		if (rosterMemberIds.isEmpty()) {
			return List.of();
		}
		return tokensOfUsers(rosterMemberRepository.findLinkedUserIds(rosterMemberIds));
	}

	private List<String> tokensOfUsers(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return List.of();
		}
		return tokenRepository.findTokensByUserIds(userIds);
	}

	private <T> T read(Supplier<T> query) {
		return readTransaction.execute(status -> query.get());
	}

	private void deliver(Plan plan, String title, Map<String, Object> data) {
		if (plan == null || plan.tokens().isEmpty()) {
			return;
		}
		sender.send(plan.tokens(), title, plan.body(), data);
	}

	private static Map<String, Object> meetupData(String type, long meetupId) {
		return Map.of("type", type, "meetupId", meetupId);
	}

	private static String when(Meetup meetup) {
		return WHEN.format(meetup.getStartsAt());
	}
}
