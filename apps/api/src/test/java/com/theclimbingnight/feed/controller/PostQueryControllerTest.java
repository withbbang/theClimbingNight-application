package com.theclimbingnight.feed.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.service.PostCommandService;
import com.theclimbingnight.feed.service.PostQueryService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class PostQueryControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean PostCommandService postCommandService;
	@MockitoBean PostQueryService postQueryService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private PostResponse response(long id) {
		return new PostResponse(id, new PostAuthorResponse(2L, "남", null), "본문", List.of(), 0, 0, false,
				OffsetDateTime.parse("2026-11-26T12:00:00Z"), null, false);
	}

	@Test
	void 비로그인_피드는_최신_3개만_주고_nextCursor가_없다() throws Exception {
		// given
		given(postQueryService.feed(isNull(), isNull(), eq(10)))
				.willReturn(new CursorResponse<>(List.of(response(3), response(2), response(1)), null));

		// when / then
		mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	void 비로그인이_cursor를_보내면_401이다() throws Exception {
		// given
		given(postQueryService.feed(isNull(), eq("abc"), eq(10)))
				.willThrow(new BusinessException(ErrorCode.AUTH_REQUIRED));

		// when / then
		mockMvc.perform(get("/api/v1/posts").param("cursor", "abc")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 비로그인이_4번째_글_상세를_부르면_401이다() throws Exception {
		// given
		given(postQueryService.detail(isNull(), eq(4L))).willThrow(new BusinessException(ErrorCode.AUTH_REQUIRED));

		// when / then
		mockMvc.perform(get("/api/v1/posts/4")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 로그인하면_size만큼_주고_다음_커서를_준다() throws Exception {
		// given
		String bearer = bearer(1L, Role.USER);
		given(postQueryService.feed(eq(1L), isNull(), eq(2)))
				.willReturn(new CursorResponse<>(List.of(response(3), response(2)), "next"));

		// when / then
		mockMvc.perform(get("/api/v1/posts").param("size", "2").header("Authorization", bearer))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.nextCursor").value("next"));
	}

	@Test
	void size_31은_검증_실패다() throws Exception {
		mockMvc.perform(get("/api/v1/posts").param("size", "31")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("size"));
		mockMvc.perform(get("/api/v1/posts").param("size", "0")).andExpect(status().isBadRequest());
		verify(postQueryService, never()).feed(isNull(), isNull(), eq(31));
	}

	@Test
	void 내_진행중_글은_로그인이_필요하고_목록을_준다() throws Exception {
		// given
		String bearer = bearer(1L, Role.USER);
		given(postQueryService.inProgress(1L)).willReturn(List.of(response(7)));

		// when / then
		mockMvc.perform(get("/api/v1/posts/mine/in-progress")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/posts/mine/in-progress").header("Authorization", bearer))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(7));
	}
}
