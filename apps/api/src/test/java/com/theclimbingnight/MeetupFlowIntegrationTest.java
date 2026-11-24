package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.attendance.service.AttendanceRecorder;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 벙 개설부터 신청·늦은 취소·블랙리스트·자동 참석 기록·보정, 탈퇴 자동 취소까지 실제 DB로 확인한다.
 * H2가 다른 통합 테스트와 공유되므로 벙·신청·참석·블랙리스트·명부·동호회원 신청 테이블은 매 테스트 전후에 비우고,
 * 이 테스트가 만든 사용자는 직접 지운다. 닉네임은 이 클래스 전용 접두사("벙플로우")로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MeetupFlowIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@TestConfiguration
	static class ClockOverride {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2030-03-01T00:00:00Z"));
		}
	}

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired MutableClock clock;
	@Autowired Clock injectedClock;
	@Autowired JwtTokenService jwtTokenService;
	@Autowired AttendanceRecorder attendanceRecorder;
	@Autowired UserRepository userRepository;
	@Autowired RosterMemberRepository rosterMemberRepository;
	@Autowired MembershipRequestRepository membershipRequestRepository;
	@Autowired MeetupRepository meetupRepository;
	@Autowired MeetupApplicationRepository applicationRepository;
	@Autowired AttendanceRepository attendanceRepository;
	@Autowired PenaltyRepository penaltyRepository;

	private final List<Long> createdUserIds = new ArrayList<>();

	@BeforeEach
	void cleanBefore() {
		cleanTables();
	}

	@AfterEach
	void cleanAfter() {
		cleanTables();
		userRepository.deleteAllById(createdUserIds);
		createdUserIds.clear();
	}

	/** 외래 키 순서: 블랙리스트 → 참석 → 신청 → 벙 → 동호회원 신청·명부. */
	private void cleanTables() {
		penaltyRepository.deleteAllInBatch();
		attendanceRepository.deleteAllInBatch();
		applicationRepository.deleteAllInBatch();
		meetupRepository.deleteAllInBatch();
		membershipRequestRepository.deleteAllInBatch();
		rosterMemberRepository.deleteAllInBatch();
	}

	private User saveUser(String nickname, Role role) {
		User user = User.create(nickname);
		user.changeRole(role);
		User saved = userRepository.saveAndFlush(user);
		createdUserIds.add(saved.getId());
		return saved;
	}

	/** 명부와 연결된 동호회원. */
	private Member saveMember(String nickname) {
		User user = saveUser(nickname, Role.MEMBER);
		RosterMember roster = RosterMember.create(nickname, null, RosterSource.APP);
		roster.linkTo(user);
		return new Member(user, rosterMemberRepository.saveAndFlush(roster).getId());
	}

	private record Member(User user, long rosterId) {
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user.getId()).token();
	}

	private JsonNode json(ResultActions actions) throws Exception {
		return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private long createMeetup(User admin, Instant startsAt, Instant endsAt, int capacity, boolean visible)
			throws Exception {
		String body = "{\"title\":\"벙플로우 정기벙\",\"placeName\":\"더클라임\",\"startsAt\":\""
				+ startsAt.atOffset(ZoneOffset.ofHours(9)) + "\",\"endsAt\":\"" + endsAt.atOffset(ZoneOffset.ofHours(9))
				+ "\",\"capacity\":" + capacity + ",\"participantsVisible\":" + visible + "}";
		return json(mockMvc.perform(post("/api/v1/meetups").header("Authorization", bearer(admin))
				.contentType(JSON).content(body)).andExpect(status().isCreated())).at("/data/id").asLong();
	}

	private JsonNode apply(User user, long meetupId) throws Exception {
		return json(mockMvc.perform(post("/api/v1/meetups/" + meetupId + "/applications")
				.header("Authorization", bearer(user))).andExpect(status().isCreated())).at("/data");
	}

	private JsonNode cancel(User user, long meetupId, String body) throws Exception {
		var request = post("/api/v1/meetups/" + meetupId + "/applications/me/cancel")
				.header("Authorization", bearer(user));
		if (body != null) {
			request = request.contentType(JSON).content(body);
		}
		return json(mockMvc.perform(request).andExpect(status().isOk())).at("/data");
	}

	private JsonNode detail(User user, long meetupId) throws Exception {
		return json(mockMvc.perform(get("/api/v1/meetups/" + meetupId).header("Authorization", bearer(user)))
				.andExpect(status().isOk())).at("/data");
	}

	private JsonNode applications(User admin, long meetupId) throws Exception {
		return json(mockMvc.perform(get("/api/v1/meetups/" + meetupId + "/applications")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data");
	}

	private static JsonNode byRoster(JsonNode applications, long rosterId) {
		for (JsonNode item : applications) {
			if (item.at("/rosterMemberId").asLong() == rosterId) {
				return item;
			}
		}
		throw new AssertionError("신청 없음: 명부 " + rosterId);
	}

	private long rosterPenaltyTotal(User admin, long rosterId) throws Exception {
		return json(mockMvc.perform(get("/api/v1/roster-members/" + rosterId + "/penalties")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data/totalCount").asLong();
	}

	private void correct(User admin, long attendanceId, String result) throws Exception {
		mockMvc.perform(put("/api/v1/attendances/" + attendanceId).header("Authorization", bearer(admin))
				.contentType(JSON).content("{\"result\":\"" + result + "\"}")).andExpect(status().isOk());
	}

	@Test
	void 개설부터_신청_늦은_취소_자동_기록_보정까지() throws Exception {
		// given
		assertThat(injectedClock).isSameAs(clock);
		User admin = saveUser("벙플로우운영", Role.ADMIN);
		Member a = saveMember("벙플로우A");
		Member b = saveMember("벙플로우B");
		Member c = saveMember("벙플로우C");
		Instant startsAt = clock.instant().plus(Duration.ofDays(3));
		Instant endsAt = startsAt.plus(Duration.ofHours(3));

		// when: 정원 2, 명단 비공개 벙에 세 명이 신청한다
		long meetupId = createMeetup(admin, startsAt, endsAt, 2, false);
		JsonNode aApplication = apply(a.user(), meetupId);
		JsonNode bApplication = apply(b.user(), meetupId);
		JsonNode cApplication = apply(c.user(), meetupId);

		// then
		assertThat(aApplication.at("/status").asText()).isEqualTo("CONFIRMED");
		assertThat(bApplication.at("/status").asText()).isEqualTo("CONFIRMED");
		assertThat(cApplication.at("/status").asText()).isEqualTo("WAITLISTED");
		assertThat(cApplication.at("/waitlistPosition").asInt()).isEqualTo(1);
		JsonNode cDetail = detail(c.user(), meetupId);
		assertThat(cDetail.at("/participants").isNull()).isTrue();
		assertThat(cDetail.at("/my/waitlistPosition").asInt()).isEqualTo(1);
		JsonNode all = applications(admin, meetupId);
		assertThat(all.size()).isEqualTo(3);
		assertThat(all.at("/2/rosterMemberId").asLong()).isEqualTo(c.rosterId());

		// when: 시작 2시간 전에 A가 취소한다
		clock.advance(Duration.between(clock.instant(), startsAt.minus(Duration.ofHours(2))));
		JsonNode aCanceled = cancel(a.user(), meetupId, "{\"reason\":\"야근\"}");

		// then: 늦은 취소로 표시되고 C가 확정된다
		assertThat(aCanceled.at("/lateCancel").asBoolean()).isTrue();
		assertThat(aCanceled.at("/cancelReason").asText()).isEqualTo("야근");
		assertThat(detail(c.user(), meetupId).at("/my/status").asText()).isEqualTo("CONFIRMED");

		// when: 기준 시각 뒤에 확정된 C가 취소한다
		JsonNode cCanceled = cancel(c.user(), meetupId, null);

		// then
		assertThat(cCanceled.at("/lateCancel").asBoolean()).isFalse();

		// when: A의 늦은 취소에 블랙리스트를 매긴다
		long aApplicationId = aApplication.at("/applicationId").asLong();
		mockMvc.perform(post("/api/v1/meetup-applications/" + aApplicationId + "/penalties")
				.header("Authorization", bearer(admin))).andExpect(status().isCreated());

		// then
		JsonNode mine = json(mockMvc.perform(get("/api/v1/penalties/me").header("Authorization", bearer(a.user())))
				.andExpect(status().isOk())).at("/data");
		assertThat(mine.size()).isEqualTo(1);
		assertThat(mine.at("/0/type").asText()).isEqualTo("LATE_CANCEL");
		assertThat(mine.at("/0/active").asBoolean()).isTrue();
		mockMvc.perform(post("/api/v1/meetup-applications/" + aApplicationId + "/penalties")
				.header("Authorization", bearer(admin)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PENALTY_ALREADY_EXISTS"));
		JsonNode summaries = json(mockMvc.perform(get("/api/v1/penalties?page=0&size=20")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data");
		assertThat(summaries.at("/totalElements").asLong()).isEqualTo(1);
		assertThat(summaries.at("/items/0/rosterMemberId").asLong()).isEqualTo(a.rosterId());
		assertThat(summaries.at("/items/0/recentCount").asLong()).isEqualTo(1);
		assertThat(byRoster(applications(admin, meetupId), a.rosterId()).at("/lateCancelPenalized").asBoolean())
				.isTrue();

		// when: 벙이 끝난 뒤 자동 기록을 돌린다
		clock.advance(Duration.between(clock.instant(), endsAt.plus(Duration.ofMinutes(1))));
		boolean recorded = attendanceRecorder.record(meetupId);

		// then: 확정자 B만 참석으로 기록된다
		assertThat(recorded).isTrue();
		JsonNode afterRecord = applications(admin, meetupId);
		JsonNode bRow = byRoster(afterRecord, b.rosterId());
		assertThat(bRow.at("/attendanceResult").asText()).isEqualTo("ATTENDED");
		assertThat(byRoster(afterRecord, a.rosterId()).at("/attendanceId").isNull()).isTrue();
		assertThat(byRoster(afterRecord, c.rosterId()).at("/attendanceId").isNull()).isTrue();

		// when: B를 노쇼로 보정한다
		long bAttendanceId = bRow.at("/attendanceId").asLong();
		correct(admin, bAttendanceId, "NO_SHOW");

		// then
		assertThat(rosterPenaltyTotal(admin, b.rosterId())).isEqualTo(1);

		// when: 다시 참석으로 보정한다
		correct(admin, bAttendanceId, "ATTENDED");

		// then
		assertThat(rosterPenaltyTotal(admin, b.rosterId())).isZero();
	}

	@Test
	void 탈퇴하면_확정_자리가_대기자에게_넘어간다() throws Exception {
		// given: 정원 1, A 확정, B 대기
		User admin = saveUser("벙플로우운영2", Role.ADMIN);
		Member a = saveMember("벙플로우A2");
		Member b = saveMember("벙플로우B2");
		Instant startsAt = clock.instant().plus(Duration.ofDays(3));
		long meetupId = createMeetup(admin, startsAt, startsAt.plus(Duration.ofHours(3)), 1, true);
		assertThat(apply(a.user(), meetupId).at("/status").asText()).isEqualTo("CONFIRMED");
		assertThat(apply(b.user(), meetupId).at("/status").asText()).isEqualTo("WAITLISTED");

		// when: A가 탈퇴한다
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(a.user())))
				.andExpect(status().isNoContent());

		// then: B가 확정되고, A의 신청은 늦은 취소 없이 취소되며 명부는 연결만 끊긴다
		assertThat(detail(b.user(), meetupId).at("/my/status").asText()).isEqualTo("CONFIRMED");
		JsonNode aRow = byRoster(applications(admin, meetupId), a.rosterId());
		assertThat(aRow.at("/status").asText()).isEqualTo("CANCELED");
		assertThat(aRow.at("/lateCancel").asBoolean()).isFalse();
		assertThat(aRow.at("/userId").isNull()).isTrue();
		assertThat(aRow.at("/somoimNickname").asText()).isEqualTo("벙플로우A2");
		assertThat(rosterMemberRepository.findById(a.rosterId())).isPresent();
	}

	@Test
	void 자격을_해제하면_확정_자리가_대기자에게_넘어간다() throws Exception {
		// given: 정원 1, A 확정, B 대기
		User admin = saveUser("벙플로우운영3", Role.ADMIN);
		Member a = saveMember("벙플로우A3");
		Member b = saveMember("벙플로우B3");
		Instant startsAt = clock.instant().plus(Duration.ofDays(3));
		long meetupId = createMeetup(admin, startsAt, startsAt.plus(Duration.ofHours(3)), 1, true);
		assertThat(apply(a.user(), meetupId).at("/status").asText()).isEqualTo("CONFIRMED");
		assertThat(apply(b.user(), meetupId).at("/status").asText()).isEqualTo("WAITLISTED");

		// when: 운영진이 A의 자격을 해제한다
		mockMvc.perform(delete("/api/v1/members/" + a.user().getId()).header("Authorization", bearer(admin)))
				.andExpect(status().isNoContent());

		// then: B가 확정되고, A의 신청은 늦은 취소 없이 사유와 함께 취소되며 명부 연결은 남는다
		assertThat(detail(b.user(), meetupId).at("/my/status").asText()).isEqualTo("CONFIRMED");
		JsonNode aRow = byRoster(applications(admin, meetupId), a.rosterId());
		assertThat(aRow.at("/status").asText()).isEqualTo("CANCELED");
		assertThat(aRow.at("/lateCancel").asBoolean()).isFalse();
		assertThat(aRow.at("/cancelReason").asText()).isEqualTo("동호회원 자격 해제");
		assertThat(aRow.at("/userId").asLong()).isEqualTo(a.user().getId());
	}
}
