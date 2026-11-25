package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.entity.MeetupStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.meetup.service.MeetupAdminService;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.notice.dto.NoticeRequest;
import com.theclimbingnight.notice.repository.NoticeRepository;
import com.theclimbingnight.notice.service.NoticeService;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.push.dto.NotificationSettingRequest;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.push.expo.PushGateway;
import com.theclimbingnight.push.expo.PushMessage;
import com.theclimbingnight.push.expo.PushReceipt;
import com.theclimbingnight.push.expo.PushTicketResult;
import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.push.repository.PushTicketRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.push.service.NotificationSettingService;
import com.theclimbingnight.push.service.PushTokenService;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.entity.UserStatus;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 도메인 이벤트가 커밋 뒤에 푸시로 이어지는 흐름을 실제 DB로 확인한다. 발송은 기록용 게이트웨이가 받고,
 * pushExecutor는 SyncTaskExecutor로 바꿔 커밋 직후 같은 스레드에서 끝나게 한다 (원래 트랜잭션 자원이 아직 묶인 단계).
 * 예약 작업은 꺼서(app.scheduling.enabled=false) 공유 H2를 건드리지 않게 한다. H2는 다른 통합 테스트와 공유되므로
 * 푸시·벙·신청·명부 테이블은 매 테스트 전후에 비우고, 닉네임은 이 클래스 전용 접두사("푸시플로우")로 둔다.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
class PushFlowIntegrationTest {

	@TestConfiguration
	static class GatewayOverride {

		@Bean
		@Primary
		RecordingGateway recordingGateway() {
			return new RecordingGateway();
		}
	}

	static class RecordingGateway implements PushGateway {

		final List<PushMessage> sent = new CopyOnWriteArrayList<>();
		final Set<String> invalidTokens = ConcurrentHashMap.newKeySet();

		@Override
		public List<PushTicketResult> send(List<PushMessage> messages) {
			sent.addAll(messages);
			return messages.stream().map(message -> invalidTokens.contains(message.to())
					? new PushTicketResult(null, "DeviceNotRegistered")
					: new PushTicketResult(UUID.randomUUID().toString(), null)).toList();
		}

		@Override
		public Map<String, PushReceipt> receipts(List<String> ticketIds) {
			return Map.of();
		}

		List<String> tokens() {
			return sent.stream().map(PushMessage::to).toList();
		}
	}

	@TestBean(name = "pushExecutor")
	TaskExecutor pushExecutor;

	static TaskExecutor pushExecutor() {
		return new SyncTaskExecutor();
	}

	@Autowired RecordingGateway gateway;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired UserRepository userRepository;
	@Autowired UserService userService;
	@Autowired RosterMemberRepository rosterMemberRepository;
	@Autowired MembershipRequestRepository membershipRequestRepository;
	@Autowired MeetupRepository meetupRepository;
	@Autowired MeetupApplicationRepository applicationRepository;
	@Autowired AttendanceRepository attendanceRepository;
	@Autowired PenaltyRepository penaltyRepository;
	@Autowired NoticeRepository noticeRepository;
	@Autowired PushTokenRepository pushTokenRepository;
	@Autowired PushTicketRepository pushTicketRepository;
	@Autowired NotificationSettingRepository notificationSettingRepository;
	@Autowired MeetupAdminService meetupAdminService;
	@Autowired NoticeService noticeService;
	@Autowired NotificationSettingService notificationSettingService;
	@Autowired PushTokenService pushTokenService;

	private final List<Long> createdUserIds = new ArrayList<>();
	private final List<Long> createdNoticeIds = new ArrayList<>();

	@BeforeEach
	void cleanBefore() {
		cleanTables();
		gateway.sent.clear();
		gateway.invalidTokens.clear();
	}

	@AfterEach
	void cleanAfter() {
		cleanTables();
		noticeRepository.deleteAllById(createdNoticeIds);
		userRepository.deleteAllById(createdUserIds);
		createdNoticeIds.clear();
		createdUserIds.clear();
		gateway.sent.clear();
		gateway.invalidTokens.clear();
	}

	/** 외래 키 순서: 푸시 → 블랙리스트 → 참석 → 신청 → 벙 → 동호회원 신청·명부. */
	private void cleanTables() {
		pushTicketRepository.deleteAllInBatch();
		pushTokenRepository.deleteAllInBatch();
		notificationSettingRepository.deleteAllInBatch();
		penaltyRepository.deleteAllInBatch();
		attendanceRepository.deleteAllInBatch();
		applicationRepository.deleteAllInBatch();
		meetupRepository.deleteAllInBatch();
		membershipRequestRepository.deleteAllInBatch();
		rosterMemberRepository.deleteAllInBatch();
	}

	private record Member(User user, long rosterId, String token) {
	}

	private User saveUser(String nickname, Role role) {
		User user = User.create(nickname);
		user.changeRole(role);
		User saved = userRepository.saveAndFlush(user);
		createdUserIds.add(saved.getId());
		return saved;
	}

	/** 토큰 열은 ascii라 한글을 넣으면 MySQL에서 실패한다. */
	private static String token(String name) {
		return "ExponentPushToken[pushflow-" + Integer.toHexString(name.hashCode()) + "]";
	}

	/** 명부와 연결되고 토큰을 하나 등록한 동호회원. */
	private Member saveMember(String name) {
		User user = saveUser("푸시플로우" + name, Role.MEMBER);
		RosterMember roster = RosterMember.create("푸시플로우" + name, null, RosterSource.APP);
		roster.linkTo(user);
		long rosterId = rosterMemberRepository.saveAndFlush(roster).getId();
		pushTokenService.register(user.getId(), token(name), PushPlatform.IOS);
		return new Member(user, rosterId, token(name));
	}

	private Meetup saveMeetup(User host) {
		Instant startsAt = Instant.now().plus(Duration.ofDays(7));
		return meetupRepository.saveAndFlush(Meetup.create(new MeetupSpec("푸시플로우 정기벙", null, "더클라임", startsAt,
				startsAt.plus(Duration.ofHours(3)), 10, 12, false), host));
	}

	private void apply(Meetup meetup, Member member, boolean confirmed) {
		applicationRepository.saveAndFlush(MeetupApplication.create(meetup,
				rosterMemberRepository.getReferenceById(member.rosterId()), confirmed, Instant.now()));
	}

	private long publishNotice(User admin) {
		long id = noticeService.create(admin.getId(), new NoticeRequest("푸시플로우 공지", "본문", false)).id();
		createdNoticeIds.add(id);
		return id;
	}

	@Test
	void 벙을_취소하면_확정자와_대기자에게만_간다() {
		// given
		User admin = saveUser("푸시플로우운영", Role.ADMIN);
		Member confirmed = saveMember("확정");
		Member waiting = saveMember("대기");
		Member canceled = saveMember("취소");
		Member bystander = saveMember("구경");
		Meetup meetup = saveMeetup(admin);
		apply(meetup, confirmed, true);
		apply(meetup, waiting, false);
		apply(meetup, canceled, true);
		applicationRepository.findByMeetupIdAndRosterMemberId(meetup.getId(), canceled.rosterId())
				.ifPresent(a -> {
					a.cancel(false, null, Instant.now());
					applicationRepository.saveAndFlush(a);
				});

		// when
		meetupAdminService.cancel(meetup.getId());

		// then
		assertThat(gateway.tokens()).containsExactlyInAnyOrder(confirmed.token(), waiting.token());
		assertThat(gateway.sent).allSatisfy(message -> {
			assertThat(message.title()).isEqualTo("벙 취소");
			assertThat(message.data()).containsEntry("type", "MEETUP_CANCELED").containsEntry("meetupId",
					meetup.getId());
		});
		assertThat(bystander.token()).isNotIn(gateway.tokens());
	}

	@Test
	void 롤백되면_푸시를_보내지_않는다() {
		// given
		User admin = saveUser("푸시플로우운영", Role.ADMIN);
		Member confirmed = saveMember("확정");
		Meetup meetup = saveMeetup(admin);
		apply(meetup, confirmed, true);

		// when
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			meetupAdminService.cancel(meetup.getId());
			status.setRollbackOnly();
		});

		// then
		assertThat(gateway.sent).isEmpty();
		assertThat(meetupRepository.findById(meetup.getId()).orElseThrow().getStatus()).isEqualTo(MeetupStatus.OPEN);
	}

	@Test
	void 공지를_끈_사람에게는_공지가_가지_않는다() {
		// given
		User admin = saveUser("푸시플로우운영", Role.ADMIN);
		Member off = saveMember("끔");
		Member on = saveMember("켬");
		notificationSettingService.update(off.user().getId(), new NotificationSettingRequest(true, false));
		pushTokenService.register(admin.getId(), token("운영"), PushPlatform.ANDROID);

		// when
		long noticeId = publishNotice(admin);

		// then
		assertThat(gateway.tokens()).containsExactly(on.token());
		assertThat(gateway.sent.get(0).data()).containsEntry("type", "NOTICE_PUBLISHED").containsEntry("noticeId",
				noticeId);
	}

	@Test
	void 무효_토큰은_발송_뒤_사라진다() {
		// given
		User admin = saveUser("푸시플로우운영", Role.ADMIN);
		Member invalid = saveMember("무효");
		Member valid = saveMember("유효");
		Meetup meetup = saveMeetup(admin);
		apply(meetup, invalid, true);
		apply(meetup, valid, true);
		gateway.invalidTokens.add(invalid.token());

		// when
		meetupAdminService.cancel(meetup.getId());

		// then: REQUIRES_NEW로 저장·삭제가 커밋되어 다른 트랜잭션에서 보인다.
		assertThat(gateway.tokens()).containsExactlyInAnyOrder(invalid.token(), valid.token());
		assertThat(pushTokenRepository.findAll()).extracting(PushToken::getExpoPushToken)
				.containsExactly(valid.token());
		assertThat(pushTicketRepository.findAll()).extracting(PushTicket::getExpoPushToken)
				.containsExactly(valid.token());
	}

	@Test
	void 탈퇴하면_더는_받지_않는다() {
		// given
		User admin = saveUser("푸시플로우운영", Role.ADMIN);
		Member leaving = saveMember("탈퇴");
		Member staying = saveMember("잔류");
		notificationSettingService.update(leaving.user().getId(), new NotificationSettingRequest(true, true));
		assertThat(notificationSettingRepository.findById(leaving.user().getId())).isPresent();

		// when
		userService.withdraw(leaving.user().getId());
		publishNotice(admin);

		// then
		assertThat(userRepository.findById(leaving.user().getId()).orElseThrow().getStatus())
				.isEqualTo(UserStatus.WITHDRAWN);
		assertThat(pushTokenRepository.findAll()).extracting(PushToken::getExpoPushToken)
				.containsExactly(staying.token());
		assertThat(notificationSettingRepository.findById(leaving.user().getId())).isEmpty();
		assertThat(gateway.tokens()).containsExactly(staying.token());
	}

	@Test
	void 같은_토큰을_다른_사용자가_등록하면_이전_사용자_푸시는_그_토큰으로_가지_않는다() {
		// given: A가 쓰던 기기에 B가 로그인해 같은 토큰을 등록했다.
		User admin = saveUser("푸시플로우운영", Role.ADMIN);
		Member a = saveMember("에이");
		Member b = saveMember("비");
		Meetup meetup = saveMeetup(admin);
		apply(meetup, a, true);
		pushTokenService.register(b.user().getId(), a.token(), PushPlatform.IOS);
		assertThat(pushTokenRepository.findAll()).filteredOn(t -> t.getExpoPushToken().equals(a.token()))
				.extracting(PushToken::getUserId).containsExactly(b.user().getId());

		// when
		meetupAdminService.cancel(meetup.getId());

		// then
		assertThat(gateway.sent).isEmpty();
	}
}
