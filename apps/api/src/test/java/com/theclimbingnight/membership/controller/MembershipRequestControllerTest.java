package com.theclimbingnight.membership.controller;

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
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.service.MembershipService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
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

	private String bearer(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", Role.USER);
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

	private MembershipRequestResponse response() {
		return new MembershipRequestResponse(1L, "철수", MembershipRequestStatus.PENDING, null,
				Instant.parse("2026-01-01T00:00:00Z"), null);
	}
}
