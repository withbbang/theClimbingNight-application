package com.theclimbingnight.moderation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.moderation.dto.ReportCreateRequest;
import com.theclimbingnight.moderation.dto.ReportTargetActionRequest.Action;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.service.ModerationService;
import com.theclimbingnight.moderation.service.ReportService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ReportController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class ReportControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean ReportService reportService;
	@MockitoBean ModerationService moderationService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private ResultActions report(String bearer, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/reports").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void 신고하면_본문_없이_204다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when / then
		report(bearer, "{\"targetType\":\"POST\",\"targetId\":50,\"reason\":\"PORTRAIT_PRIVACY\"}")
				.andExpect(status().isNoContent());
		verify(reportService).submit(3L,
				new ReportCreateRequest(ReportTargetType.POST, 50L, ReportReason.PORTRAIT_PRIVACY, null));
	}

	@Test
	void OTHER인데_detail이_비면_검증_실패다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when / then
		report(bearer, "{\"targetType\":\"POST\",\"targetId\":50,\"reason\":\"OTHER\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		report(bearer, "{\"targetType\":\"POST\",\"targetId\":50,\"reason\":\"OTHER\",\"detail\":\"   \"}")
				.andExpect(status().isBadRequest());
		verify(reportService, never()).submit(anyLong(), any());
		report(bearer, "{\"targetType\":\"POST\",\"targetId\":50,\"reason\":\"OTHER\",\"detail\":\"사유\"}")
				.andExpect(status().isNoContent());
	}

	@Test
	void detail은_500자까지다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);
		String body = "{\"targetType\":\"USER\",\"targetId\":9,\"reason\":\"SPAM\",\"detail\":\"%s\"}";

		// when / then
		report(bearer, body.formatted("가".repeat(500))).andExpect(status().isNoContent());
		report(bearer, body.formatted("가".repeat(501))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("detail"));
	}

	@Test
	void 댓글_신고는_204다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when / then
		report(bearer, "{\"targetType\":\"COMMENT\",\"targetId\":50,\"reason\":\"SPAM\"}")
				.andExpect(status().isNoContent());
		verify(reportService).submit(3L,
				new ReportCreateRequest(ReportTargetType.COMMENT, 50L, ReportReason.SPAM, null));
	}

	@Test
	void 댓글을_초상권_사유로_신고하면_400이고_대상_없는_요청도_400이다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);

		// when / then
		report(bearer, "{\"targetType\":\"COMMENT\",\"targetId\":50,\"reason\":\"PORTRAIT_PRIVACY\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		report(bearer, "{\"targetId\":50,\"reason\":\"SPAM\"}").andExpect(status().isBadRequest());
		verify(reportService, never()).submit(anyLong(), any());
	}

	@Test
	void ADMIN이_아니면_신고_목록은_403이다() throws Exception {
		// given
		String member = bearer(7L, Role.MEMBER);
		String admin = bearer(1L, Role.ADMIN);
		given(moderationService.list(0, 20)).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		// when / then
		mockMvc.perform(get("/api/v1/report-targets").header("Authorization", member))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(moderationService, never()).list(anyInt(), anyInt());
		mockMvc.perform(get("/api/v1/report-targets").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray());
	}

	@Test
	void 운영진_조치는_대상과_행동을_넘기고_204다() throws Exception {
		// given
		String admin = bearer(1L, Role.ADMIN);
		String member = bearer(7L, Role.MEMBER);

		// when / then
		mockMvc.perform(post("/api/v1/report-targets/POST/50/actions").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE\"}"))
				.andExpect(status().isNoContent());
		verify(moderationService).act(1L, ReportTargetType.POST, 50L, Action.HIDE);
		mockMvc.perform(post("/api/v1/report-targets/POST/50/actions").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/report-targets/POST/50/actions").header("Authorization", member)
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE\"}"))
				.andExpect(status().isForbidden());
		verify(moderationService, never()).act(eq(7L), any(), anyLong(), any());
	}
}
