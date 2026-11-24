package com.theclimbingnight.push.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.push.dto.NotificationSettingRequest;
import com.theclimbingnight.push.dto.NotificationSettingResponse;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.service.NotificationSettingService;
import com.theclimbingnight.push.service.PushTokenService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PushTokenController.class, NotificationSettingController.class})
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class PushTokenControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;
	@MockitoBean UserRepository userRepository;
	@MockitoBean PushTokenService pushTokenService;
	@MockitoBean NotificationSettingService notificationSettingService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private static String body(String token) {
		return "{\"expoPushToken\":\"" + token + "\",\"platform\":\"IOS\"}";
	}

	@Test
	void 토큰을_등록하면_204다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(put("/api/v1/push-tokens").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body("ExponentPushToken[abc-DEF_1]")))
				.andExpect(status().isNoContent());
		verify(pushTokenService).register(3L, "ExponentPushToken[abc-DEF_1]", PushPlatform.IOS);
	}

	@Test
	void 구형_토큰_접두사도_받는다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(put("/api/v1/push-tokens").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body("ExpoPushToken[abc]")))
				.andExpect(status().isNoContent());
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "ExponentPushToken[]", "Exponent PushToken[abc]", "ExponentPushToken[a b]",
			"ExponentPushToken[abc]x", " ExponentPushToken[abc]"})
	void 형식이_다른_토큰은_400이다(String token) throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(put("/api/v1/push-tokens").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verify(pushTokenService, never()).register(anyLong(), any(), any());
	}

	@Test
	void 토큰이_200자를_넘으면_400이다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(put("/api/v1/push-tokens").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body("ExponentPushToken[" + "a".repeat(201) + "]")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 플랫폼이_없으면_400이다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(put("/api/v1/push-tokens").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"expoPushToken\":\"ExponentPushToken[abc]\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		// given, when, then
		mockMvc.perform(put("/api/v1/push-tokens").contentType(MediaType.APPLICATION_JSON)
				.content(body("ExponentPushToken[abc]")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 알림_설정을_조회한다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);
		given(notificationSettingService.getMine(3L)).willReturn(new NotificationSettingResponse(true, false));

		// when, then
		mockMvc.perform(get("/api/v1/notification-settings/me").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.newMeetup").value(true))
				.andExpect(jsonPath("$.data.newNotice").value(false));
	}

	@Test
	void 알림_설정을_바꾼다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);
		given(notificationSettingService.update(3L, new NotificationSettingRequest(false, true)))
				.willReturn(new NotificationSettingResponse(false, true));

		// when, then
		mockMvc.perform(put("/api/v1/notification-settings/me").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"newMeetup\":false,\"newNotice\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.newMeetup").value(false));
	}

	@Test
	void 알림_설정_값이_빠지면_400이다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(put("/api/v1/notification-settings/me").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"newMeetup\":false}"))
				.andExpect(status().isBadRequest());
	}
}
