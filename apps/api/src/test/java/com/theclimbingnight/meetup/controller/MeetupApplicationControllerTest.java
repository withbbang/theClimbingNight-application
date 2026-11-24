package com.theclimbingnight.meetup.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.theclimbingnight.meetup.dto.MyApplicationResponse;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.service.MeetupApplicationService;
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

@WebMvcTest(MeetupApplicationController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class MeetupApplicationControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	MeetupApplicationService meetupApplicationService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void USER가_신청하면_403이다() throws Exception {
		// given
		String bearer = bearer(9L, Role.USER);

		// when, then
		mockMvc.perform(post("/api/v1/meetups/5/applications").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(meetupApplicationService, never()).apply(anyLong(), anyLong());
	}

	@Test
	void MEMBER가_신청하면_201이고_대기_순번이_나온다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		given(meetupApplicationService.apply(5L, 7L)).willReturn(
				new MyApplicationResponse(100L, MeetupApplicationStatus.WAITLISTED, 2, false, null));

		// when, then
		mockMvc.perform(post("/api/v1/meetups/5/applications").header("Authorization", bearer))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.applicationId").value(100))
				.andExpect(jsonPath("$.data.status").value("WAITLISTED"))
				.andExpect(jsonPath("$.data.waitlistPosition").value(2));
	}

	@Test
	void 취소_사유가_201자면_400이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		String body = "{\"reason\":\"" + "가".repeat(201) + "\"}";

		// when, then
		mockMvc.perform(post("/api/v1/meetups/5/applications/me/cancel").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verify(meetupApplicationService, never()).cancel(anyLong(), anyLong(), anyString());
	}

	@Test
	void 취소_사유가_200자면_받는다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		String reason = "가".repeat(200);
		given(meetupApplicationService.cancel(5L, 7L, reason)).willReturn(
				new MyApplicationResponse(100L, MeetupApplicationStatus.CANCELED, null, true, reason));

		// when, then
		mockMvc.perform(post("/api/v1/meetups/5/applications/me/cancel").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.lateCancel").value(true));
	}

	@Test
	void 본문_없이_취소하면_사유_없이_취소한다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		given(meetupApplicationService.cancel(5L, 7L, null)).willReturn(
				new MyApplicationResponse(100L, MeetupApplicationStatus.CANCELED, null, false, null));

		// when, then
		mockMvc.perform(post("/api/v1/meetups/5/applications/me/cancel").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"));
		verify(meetupApplicationService).cancel(5L, 7L, null);
	}

	@Test
	void 내보내기는_ADMIN만_하고_204다() throws Exception {
		// given
		String admin = bearer(1L, Role.ADMIN);
		String member = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(post("/api/v1/meetup-applications/100/remove").header("Authorization", member))
				.andExpect(status().isForbidden());
		verify(meetupApplicationService, never()).remove(anyLong());
		mockMvc.perform(post("/api/v1/meetup-applications/100/remove").header("Authorization", admin))
				.andExpect(status().isNoContent());
		verify(meetupApplicationService).remove(100L);
		verify(meetupApplicationService, never()).cancel(anyLong(), anyLong(), isNull());
	}
}
