package com.theclimbingnight.profile.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.profile.dto.ProfileResponse;
import com.theclimbingnight.profile.service.BioService;
import com.theclimbingnight.profile.service.ProfileQueryService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
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

@WebMvcTest(ProfileController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class ProfileControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean ProfileQueryService profileQueryService;
	@MockitoBean BioService bioService;

	private String bearer(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", Role.USER);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/users/1/profile")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 자기소개를_고치면_프로필을_돌려준다() throws Exception {
		// given
		given(bioService.update(eq(1L), eq("x")))
				.willReturn(new ProfileResponse(1L, "climber", "x", null, 0, true));

		// when / then
		mockMvc.perform(patch("/api/v1/users/me/profile").header("Authorization", bearer(1L))
				.contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"x\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bio").value("x"));
	}

	@Test
	void 격자_size_31은_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/users/2/posts?size=31").header("Authorization", bearer(1L)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 격자_기본_size는_18이다() throws Exception {
		given(profileQueryService.posts(anyLong(), anyLong(), isNull(), eq(18)))
				.willReturn(new com.theclimbingnight.common.CursorResponse<>(java.util.List.of(), null));
		mockMvc.perform(get("/api/v1/users/2/posts").header("Authorization", bearer(1L)))
				.andExpect(status().isOk());
	}
}
