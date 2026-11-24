package com.theclimbingnight.setting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.theclimbingnight.setting.dto.AppSettingsResponse;
import com.theclimbingnight.setting.dto.AppSettingsUpdateRequest;
import com.theclimbingnight.setting.service.AppSettingService;
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

@WebMvcTest(AppSettingController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class AppSettingControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	AppSettingService appSettingService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 토큰_없이_설정을_본다() throws Exception {
		// given
		given(appSettingService.get()).willReturn(new AppSettingsResponse(null, "https://store.example"));

		// when, then
		mockMvc.perform(get("/api/v1/app-settings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.smartstoreUrl").value("https://store.example"));
	}

	@Test
	void https가_아니면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);

		// when, then
		for (String url : new String[] {"http://a.com", "javascript:alert(1)"}) {
			mockMvc.perform(put("/api/v1/app-settings").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content("{\"somoimJoinUrl\":\"" + url + "\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
		verify(appSettingService, never()).update(any(AppSettingsUpdateRequest.class));
	}

	@Test
	void 동호회원이_바꾸면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(put("/api/v1/app-settings").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"somoimJoinUrl\":\"https://a.com\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(appSettingService, never()).update(any(AppSettingsUpdateRequest.class));
	}
}
