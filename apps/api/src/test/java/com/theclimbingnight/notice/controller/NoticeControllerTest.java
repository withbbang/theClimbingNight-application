package com.theclimbingnight.notice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.theclimbingnight.notice.dto.NoticeRequest;
import com.theclimbingnight.notice.dto.NoticeResponse;
import com.theclimbingnight.notice.dto.NoticeSummaryResponse;
import com.theclimbingnight.notice.service.NoticeService;
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

@WebMvcTest(NoticeController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class NoticeControllerTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	NoticeService noticeService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private static String json(String title, String body) {
		return "{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"pinned\":false}";
	}

	@Test
	void 토큰_없이_목록과_상세를_본다() throws Exception {
		// given
		given(noticeService.list(0, 20)).willReturn(
				new PageResponse<>(List.of(new NoticeSummaryResponse(1, "공지", true, NOW)), 0, 20, 1, 1));
		given(noticeService.get(1L)).willReturn(new NoticeResponse(1, "공지", "본문", true, NOW, NOW));

		// when, then
		mockMvc.perform(get("/api/v1/notices"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].title").value("공지"));
		mockMvc.perform(get("/api/v1/notices/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("본문"));
	}

	@Test
	void 동호회원이_작성하면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(post("/api/v1/notices").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json("t", "b")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(noticeService, never()).create(anyLong(), any(NoticeRequest.class));
	}

	@Test
	void 본문은_만_자까지다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		given(noticeService.create(anyLong(), any(NoticeRequest.class)))
				.willReturn(new NoticeResponse(1, "t", "b", false, NOW, NOW));

		// when, then
		mockMvc.perform(post("/api/v1/notices").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json("t", "a".repeat(10000))))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/notices").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json("t", "a".repeat(10001))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}
}
