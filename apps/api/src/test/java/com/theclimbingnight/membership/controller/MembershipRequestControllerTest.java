package com.theclimbingnight.membership.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.service.MembershipReviewService;
import com.theclimbingnight.membership.service.MembershipService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MembershipRequestController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class MembershipRequestControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	MembershipService membershipService;

	@MockitoBean
	MembershipReviewService membershipReviewService;

	private String bearer(long id) {
		return bearer(id, Role.USER);
	}

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private String body(String nickname) {
		return "{\"somoimNickname\":\"" + nickname + "\"}";
	}

	@Test
	void 비로그인이면_401이다() throws Exception {
		// when, then
		mockMvc.perform(post("/api/v1/membership-requests").contentType(MediaType.APPLICATION_JSON)
				.content(body("철수")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
		mockMvc.perform(get("/api/v1/membership-requests/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void 닉네임이_공백뿐이거나_51자면_400이다() throws Exception {
		// given
		String bearer = bearer(7L);
		given(membershipService.apply(7L, "a".repeat(50))).willReturn(response());

		// when, then
		for (String invalid : new String[] {"   ", "a".repeat(51)}) {
			mockMvc.perform(post("/api/v1/membership-requests").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content(body(invalid)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
		mockMvc.perform(post("/api/v1/membership-requests").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body("a".repeat(50))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	void 신청한_적_없으면_204다() throws Exception {
		// given
		String bearer = bearer(7L);
		given(membershipService.getMine(7L)).willReturn(Optional.empty());

		// when, then
		mockMvc.perform(get("/api/v1/membership-requests/me").header("Authorization", bearer))
				.andExpect(status().isNoContent());
	}

	@Test
	void MEMBER가_심사_목록을_보면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/membership-requests").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@Test
	void size가_101이면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		given(membershipReviewService.list(MembershipRequestStatus.PENDING, 0, 100))
				.willReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

		// when, then
		for (String query : new String[] {"size=101", "size=0", "page=-1"}) {
			mockMvc.perform(get("/api/v1/membership-requests?" + query).header("Authorization", bearer))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
		mockMvc.perform(get("/api/v1/membership-requests?size=100").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(100));
	}

	@Test
	void 승인_요청에_명부ID와_새로만들기를_둘_다_주거나_둘_다_안_주면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		given(membershipReviewService.approve(anyLong(), anyLong(), any())).willReturn(response());

		// when, then
		for (String invalid : new String[] {"{\"rosterMemberId\":3,\"createNew\":true}", "{}",
				"{\"createNew\":false}"}) {
			mockMvc.perform(post("/api/v1/membership-requests/5/approve").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content(invalid))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
		for (String valid : new String[] {"{\"rosterMemberId\":3}", "{\"createNew\":true}"}) {
			mockMvc.perform(post("/api/v1/membership-requests/5/approve").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content(valid))
					.andExpect(status().isOk());
		}
	}

	@Test
	void 거절_사유가_비었거나_201자면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		given(membershipReviewService.reject(anyLong(), anyLong(), any())).willReturn(response());

		// when, then
		for (String invalid : new String[] {"   ", "a".repeat(201)}) {
			mockMvc.perform(post("/api/v1/membership-requests/5/reject").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + invalid + "\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
		mockMvc.perform(post("/api/v1/membership-requests/5/reject").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "a".repeat(200) + "\"}"))
				.andExpect(status().isOk());
	}

	private MembershipRequestResponse response() {
		return new MembershipRequestResponse(1L, "철수", MembershipRequestStatus.PENDING, null,
				Instant.parse("2026-01-01T00:00:00Z"), null);
	}
}
