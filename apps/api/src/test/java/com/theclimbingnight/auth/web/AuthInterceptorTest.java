package com.theclimbingnight.auth.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProbeController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class AuthInterceptorTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	private User user(long id, Role role, boolean withdrawn) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		if (withdrawn) {
			user.withdraw(java.time.Instant.parse("2026-01-01T00:00:00Z"));
		}
		return user;
	}

	private String bearer(long id) {
		return "Bearer " + jwtTokenService.issueAccessToken(id);
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/api/probe/user"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void Bearer가_아닌_헤더는_401이다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/api/probe/user").header("Authorization", "Token abc"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 유효한_토큰이면_사용자ID와_역할이_주입된다() throws Exception {
		// given
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.USER, false)));

		// when, then
		mockMvc.perform(get("/api/probe/user").header("Authorization", bearer(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void 탈퇴한_사용자의_유효한_토큰이면_401이다() throws Exception {
		// given
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.USER, true)));

		// when, then
		mockMvc.perform(get("/api/probe/user").header("Authorization", bearer(7L)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 없는_사용자의_토큰이면_401이다() throws Exception {
		// given
		given(userRepository.findById(7L)).willReturn(Optional.empty());

		// when, then
		mockMvc.perform(get("/api/probe/user").header("Authorization", bearer(7L)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void USER가_MEMBER_전용을_호출하면_403이다() throws Exception {
		// given
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.USER, false)));

		// when, then
		mockMvc.perform(get("/api/probe/member").header("Authorization", bearer(7L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@Test
	void ADMIN은_MEMBER_전용도_호출할_수_있다() throws Exception {
		// given
		given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, Role.ADMIN, false)));

		// when, then
		mockMvc.perform(get("/api/probe/member").header("Authorization", bearer(1L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void DB의_역할이_바뀌면_토큰과_무관하게_반영된다() throws Exception {
		// given
		String header = bearer(7L);
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.MEMBER, false)));
		mockMvc.perform(get("/api/probe/member").header("Authorization", header)).andExpect(status().isOk());
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.USER, false)));

		// when, then
		mockMvc.perform(get("/api/probe/member").header("Authorization", header))
				.andExpect(status().isForbidden());
	}

	@Test
	void 공개_API는_토큰_없이_200이고_사용자는_null이다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/api/probe/public"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.anonymous").value(true));
	}

	@Test
	void 공개_API에_잘못된_토큰을_보내도_200이고_사용자는_null이다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/api/probe/public").header("Authorization", "Bearer garbage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.anonymous").value(true));
	}

	@Test
	void 공개_API라도_RequireRole이_있으면_토큰_없이는_401이다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/api/probe/public-admin"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 공개_API라도_RequireRole이_있으면_역할이_부족하면_403이다() throws Exception {
		// given
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.USER, false)));

		// when, then
		mockMvc.perform(get("/api/probe/public-admin").header("Authorization", bearer(7L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@Test
	void 공개_API에_유효한_토큰을_보내면_사용자가_주입된다() throws Exception {
		// given
		given(userRepository.findById(7L)).willReturn(Optional.of(user(7L, Role.USER, false)));

		// when, then
		mockMvc.perform(get("/api/probe/public").header("Authorization", bearer(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.anonymous").value(false))
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.role").value("USER"));
	}

}
