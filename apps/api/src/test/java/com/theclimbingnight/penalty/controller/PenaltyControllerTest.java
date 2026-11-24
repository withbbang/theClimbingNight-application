package com.theclimbingnight.penalty.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.theclimbingnight.penalty.dto.PenaltyResponse;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.service.PenaltyService;
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

@WebMvcTest(PenaltyController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class PenaltyControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	PenaltyService penaltyService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void MEMBER가_블랙리스트_목록을_보면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/penalties").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(penaltyService, never()).summaries(anyInt(), anyInt());
	}

	@Test
	void USER가_내_블랙리스트를_보면_403이다() throws Exception {
		// given
		String bearer = bearer(9L, Role.USER);

		// when, then
		mockMvc.perform(get("/api/v1/penalties/me").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(penaltyService, never()).mine(anyLong());
	}

	@Test
	void MEMBER는_내_블랙리스트를_본다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		given(penaltyService.mine(7L)).willReturn(List.of(new PenaltyResponse(3L, 10L, "정기벙",
				Instant.parse("2026-12-01T10:00:00Z"), PenaltyType.LATE_CANCEL, true, null)));

		// when, then
		mockMvc.perform(get("/api/v1/penalties/me").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(3))
				.andExpect(jsonPath("$.data[0].type").value("LATE_CANCEL"));
	}

	@Test
	void 페이지_크기가_100을_넘으면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);

		// when, then
		mockMvc.perform(get("/api/v1/penalties?size=101").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 취소_사유가_비면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);

		// when, then
		mockMvc.perform(post("/api/v1/penalties/3/revoke").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verify(penaltyService, never()).revoke(anyLong(), anyLong(), anyString());
	}

	@Test
	void MEMBER가_명부별_기록을_보면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/roster-members/5/penalties").header("Authorization", bearer))
				.andExpect(status().isForbidden());
		verify(penaltyService, never()).ofRoster(anyLong());
	}
}
