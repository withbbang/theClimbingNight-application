package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 명부 등록부터 승인·해제·재승인·탈퇴까지 실제 DB로 확인한다.
 * H2가 다른 통합 테스트와 공유되므로, 명부·신청 테이블은 매 테스트 전후에 비우고
 * 이 테스트가 만든 사용자는 전후에 직접 지운다. 닉네임은 이 클래스 전용 접두사("플로우")로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MembershipFlowIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	UserRepository userRepository;

	@Autowired
	RosterMemberRepository rosterMemberRepository;

	@Autowired
	MembershipRequestRepository membershipRequestRepository;

	@Autowired
	JwtTokenService jwtTokenService;

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

	private void cleanTables() {
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

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user.getId()).token();
	}

	private JsonNode json(ResultActions actions) throws Exception {
		return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private ResultActions upload(User admin, String csv) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "roster.csv", "text/csv",
				csv.getBytes(StandardCharsets.UTF_8));
		return mockMvc.perform(multipart("/api/v1/roster-members/import").file(file)
				.header("Authorization", bearer(admin)));
	}

	private long apply(User user, String somoimNickname) throws Exception {
		return json(mockMvc.perform(post("/api/v1/membership-requests").header("Authorization", bearer(user))
				.contentType(JSON).content("{\"somoimNickname\":\"" + somoimNickname + "\"}"))
				.andExpect(status().isCreated())).at("/data/id").asLong();
	}

	private JsonNode pendingList(User admin) throws Exception {
		return json(mockMvc.perform(get("/api/v1/membership-requests?status=PENDING")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data/items");
	}

	private JsonNode bulkApprove(User admin) throws Exception {
		return json(mockMvc.perform(post("/api/v1/membership-requests/bulk-approve")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data");
	}

	private String roleOf(User user) throws Exception {
		return json(mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user)))
				.andExpect(status().isOk())).at("/data/role").asText();
	}

	@Test
	void 명부_등록부터_승인_해제_재승인_탈퇴까지() throws Exception {
		// given
		User admin = saveUser("플로우운영", Role.ADMIN);
		User a = saveUser("플로우A", Role.USER);
		User b = saveUser("플로우B", Role.USER);
		User c = saveUser("플로우C", Role.USER);
		String csv = "소모임닉네임,메모\n철수,\n영희,\n";

		// when: CSV를 올리고 같은 파일을 다시 올린다
		JsonNode first = json(upload(admin, csv).andExpect(status().isOk())).at("/data");
		JsonNode second = json(upload(admin, csv).andExpect(status().isOk())).at("/data");

		// then
		assertThat(first.at("/addedCount").asInt()).isEqualTo(2);
		assertThat(second.at("/addedCount").asInt()).isZero();
		assertThat(second.at("/skipped").size()).isEqualTo(2);

		// when: 세 명이 신청한다
		apply(a, "철수");
		apply(b, "영희 ");
		apply(c, "없는사람");
		JsonNode items = pendingList(admin);

		// then
		assertThat(items.size()).isEqualTo(3);
		for (JsonNode item : items) {
			String nickname = item.at("/user/nickname").asText();
			boolean isC = nickname.equals("플로우C");
			assertThat(item.at("/bulkApprovable").asBoolean()).isEqualTo(!isC);
			assertThat(item.at("/candidates").size()).isEqualTo(isC ? 0 : 1);
		}

		// when: 일괄 승인
		JsonNode bulk = bulkApprove(admin);

		// then
		assertThat(bulk.at("/approvedCount").asInt()).isEqualTo(2);
		assertThat(bulk.at("/skippedCount").asInt()).isZero();
		assertThat(roleOf(a)).isEqualTo("MEMBER");
		assertThat(roleOf(b)).isEqualTo("MEMBER");
		assertThat(roleOf(c)).isEqualTo("USER");
		JsonNode remaining = pendingList(admin);
		assertThat(remaining.size()).isEqualTo(1);
		assertThat(remaining.get(0).at("/user/nickname").asText()).isEqualTo("플로우C");

		// when: C를 새 항목으로 승인한다
		long cRequestId = remaining.get(0).at("/id").asLong();
		mockMvc.perform(post("/api/v1/membership-requests/" + cRequestId + "/approve")
				.header("Authorization", bearer(admin)).contentType(JSON).content("{\"createNew\":true}"))
				.andExpect(status().isOk());

		// then
		assertThat(roleOf(c)).isEqualTo("MEMBER");
		JsonNode created = json(mockMvc.perform(get("/api/v1/roster-members?q=없는사람")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data/items");
		assertThat(created.size()).isEqualTo(1);
		assertThat(created.get(0).at("/source").asText()).isEqualTo("APP");
		assertThat(created.get(0).at("/somoimNickname").asText()).isEqualTo("없는사람");

		// when: A의 자격을 해제한다
		long linkedRosterId = json(mockMvc.perform(get("/api/v1/roster-members?q=철수")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data/items/0/id")
				.asLong();
		mockMvc.perform(delete("/api/v1/members/" + a.getId()).header("Authorization", bearer(admin)))
				.andExpect(status().isNoContent());

		// then
		assertThat(roleOf(a)).isEqualTo("USER");

		// when: A가 다시 신청한다
		long reapplyId = apply(a, "철수");
		JsonNode reapply = pendingList(admin);

		// then: 후보는 A에 연결된 그 항목이다
		assertThat(reapply.size()).isEqualTo(1);
		assertThat(reapply.get(0).at("/candidates").size()).isEqualTo(1);
		assertThat(reapply.get(0).at("/candidates/0/id").asLong()).isEqualTo(linkedRosterId);

		// when: 그 항목으로 승인한다
		mockMvc.perform(post("/api/v1/membership-requests/" + reapplyId + "/approve")
				.header("Authorization", bearer(admin)).contentType(JSON)
				.content("{\"rosterMemberId\":" + linkedRosterId + "}")).andExpect(status().isOk());

		// then: 같은 명부 항목에 이어진다
		assertThat(roleOf(a)).isEqualTo("MEMBER");
		JsonNode linked = json(mockMvc.perform(get("/api/v1/roster-members?q=철수")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data/items");
		assertThat(linked.size()).isEqualTo(1);
		assertThat(linked.get(0).at("/id").asLong()).isEqualTo(linkedRosterId);
		assertThat(linked.get(0).at("/userId").asLong()).isEqualTo(a.getId());

		// when: A가 탈퇴한다
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(a)))
				.andExpect(status().isNoContent());

		// then: 명부 항목은 남고 연결만 끊긴다
		JsonNode afterWithdraw = json(mockMvc.perform(get("/api/v1/roster-members?q=철수")
				.header("Authorization", bearer(admin))).andExpect(status().isOk())).at("/data/items");
		assertThat(afterWithdraw.size()).isEqualTo(1);
		assertThat(afterWithdraw.get(0).at("/id").asLong()).isEqualTo(linkedRosterId);
		assertThat(afterWithdraw.get(0).at("/userId").isNull()).isTrue();
	}

	@Test
	void 같은_닉네임_두_신청은_일괄_승인에서_빠진다() throws Exception {
		// given: 명부에 "철수" 하나, A·D가 같은 이름으로 신청
		User admin = saveUser("플로우운영2", Role.ADMIN);
		User a = saveUser("플로우A2", Role.USER);
		User d = saveUser("플로우D2", Role.USER);
		upload(admin, "소모임닉네임,메모\n철수,\n").andExpect(status().isOk());
		apply(a, "철수");
		apply(d, "철수");

		// when
		JsonNode bulk = bulkApprove(admin);

		// then
		assertThat(bulk.at("/approvedCount").asInt()).isZero();
		assertThat(roleOf(a)).isEqualTo("USER");
		assertThat(roleOf(d)).isEqualTo("USER");
		assertThat(pendingList(admin).size()).isEqualTo(2);
	}
}
