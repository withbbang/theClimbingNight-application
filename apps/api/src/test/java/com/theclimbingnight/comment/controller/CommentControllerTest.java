package com.theclimbingnight.comment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.comment.dto.CommentCreateRequest;
import com.theclimbingnight.comment.dto.CommentDisplay;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.service.CommentCommandService;
import com.theclimbingnight.comment.service.CommentQueryService;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.OffsetDateTime;
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

@WebMvcTest(CommentController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class CommentControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean CommentQueryService commentQueryService;
	@MockitoBean CommentCommandService commentCommandService;

	private String bearer(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", Role.USER);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 비로그인은_목록도_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts/5/comments")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
		mockMvc.perform(post("/api/v1/posts/5/comments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"안녕\"}")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/comments/9")).andExpect(status().isUnauthorized());
	}

	@Test
	void 작성은_201이다() throws Exception {
		// given
		String bearer = bearer(1L);
		CommentResponse response = new CommentResponse(9L, null, new PostAuthorResponse(1L, "climber"), "안녕", null,
				OffsetDateTime.now(), CommentDisplay.VISIBLE, 0, true, true);
		given(commentCommandService.create(eq(1L), eq(5L), any(CommentCreateRequest.class))).willReturn(response);

		// when / then
		mockMvc.perform(post("/api/v1/posts/5/comments").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"안녕\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(9))
				.andExpect(jsonPath("$.data.body").value("안녕"));
	}

	@Test
	void 삭제는_204이다() throws Exception {
		mockMvc.perform(delete("/api/v1/comments/9").header("Authorization", bearer(1L)))
				.andExpect(status().isNoContent());
	}

	@Test
	void 목록은_기본_size_20이다() throws Exception {
		// given
		String bearer = bearer(1L);
		given(commentQueryService.list(eq(1L), eq(Role.USER), eq(5L), isNull(), isNull(), eq(20)))
				.willReturn(new com.theclimbingnight.common.CursorResponse<>(java.util.List.of(), null));

		// when / then
		mockMvc.perform(get("/api/v1/posts/5/comments").header("Authorization", bearer))
				.andExpect(status().isOk());
	}

	@Test
	void size_0은_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts/5/comments").param("size", "0").header("Authorization", bearer(1L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 본문이_501자면_400이다() throws Exception {
		mockMvc.perform(post("/api/v1/posts/5/comments").header("Authorization", bearer(1L))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + "가".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}
}
