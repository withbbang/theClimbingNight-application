package com.theclimbingnight.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.AuthService;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.service.LoginResult;
import com.theclimbingnight.auth.service.TokenPair;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	AuthService authService;

	private TokenPair tokenPair() {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", 7L);
		return new TokenPair("access", Instant.parse("2026-01-01T00:30:00Z"), "refresh", user);
	}

	@Test
	void 로그인_응답_형식() throws Exception {
		// given
		given(authService.login(SocialProvider.KAKAO, "social"))
				.willReturn(new LoginResult.LoggedIn(tokenPair()));

		// when, then
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"KAKAO\",\"token\":\"social\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
				.andExpect(jsonPath("$.data.tokens.accessToken").value("access"))
				.andExpect(jsonPath("$.data.tokens.refreshToken").value("refresh"))
				.andExpect(jsonPath("$.data.tokens.user.id").value(7))
				.andExpect(jsonPath("$.data.tokens.user.role").value("USER"))
				.andExpect(jsonPath("$.data.signupToken").doesNotExist());
	}

	@Test
	void 처음_로그인하면_가입_토큰을_준다() throws Exception {
		// given
		given(authService.login(SocialProvider.KAKAO, "social"))
				.willReturn(new LoginResult.SignupRequired("signup-jwt"));

		// when, then
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"KAKAO\",\"token\":\"social\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
				.andExpect(jsonPath("$.data.signupToken").value("signup-jwt"))
				.andExpect(jsonPath("$.data.tokens").doesNotExist());
	}

	@Test
	void provider가_없으면_400이다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"social\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("provider"));
		verify(authService, never()).login(any(), any());
	}

	@Test
	void 소셜_토큰이_없거나_비어_있으면_400이다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"KAKAO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("token"));
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"KAKAO\",\"token\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verify(authService, never()).login(any(), any());
	}

	@Test
	void 알_수_없는_provider면_400이다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"FACEBOOK\",\"token\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 가입은_201이다() throws Exception {
		// given
		given(authService.signup("signup-jwt", "climber")).willReturn(tokenPair());

		// when, then
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"signupToken\":\"signup-jwt\",\"nickname\":\"climber\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.accessToken").value("access"))
				.andExpect(jsonPath("$.data.user.nickname").value("climber"));
	}

	@Test
	void 가입_닉네임이_비어_있으면_400이다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"signupToken\":\"signup-jwt\",\"nickname\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("nickname"));
	}

	@Test
	void 닉네임이_중복이면_409다() throws Exception {
		// given
		given(authService.signup("signup-jwt", "climber"))
				.willThrow(new BusinessException(ErrorCode.USER_NICKNAME_TAKEN));

		// when, then
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"signupToken\":\"signup-jwt\",\"nickname\":\"climber\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("USER_NICKNAME_TAKEN"));
	}

	@Test
	void 토큰_갱신_응답_형식() throws Exception {
		// given
		given(authService.refresh("old")).willReturn(tokenPair());

		// when, then
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"old\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.refreshToken").value("refresh"));
	}

	@Test
	void 갱신_토큰이_비어_있으면_400이다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 로그아웃은_204다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"old\"}"))
				.andExpect(status().isNoContent());
		verify(authService).logout("old");
	}

}
