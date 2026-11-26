package com.theclimbingnight.feed.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.theclimbingnight.feed.dto.PostDraftResponse;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.feed.service.PostCommandService;
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

@WebMvcTest(PostController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class PostControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean PostCommandService postCommandService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private static String photos(int count, long byteSize, String contentType) {
		StringBuilder sb = new StringBuilder("{\"media\":[");
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"contentType\":\"").append(contentType).append("\",\"byteSize\":").append(byteSize)
					.append(",\"width\":800,\"height\":600}");
		}
		return sb.append("]}").toString();
	}

	private ResultActionsHolder create(String body) throws Exception {
		return new ResultActionsHolder(mockMvc.perform(post("/api/v1/posts")
				.header("Authorization", bearer(1L, Role.USER)).contentType(MediaType.APPLICATION_JSON).content(body)));
	}

	private record ResultActionsHolder(org.springframework.test.web.servlet.ResultActions actions) {
	}

	@Test
	void 사진_10장은_초안이_되고_11장은_검증_실패다() throws Exception {
		// given
		given(postCommandService.createDraft(eq(1L), any())).willReturn(new PostDraftResponse(5L, List.of()));

		// when / then
		create(photos(10, 1000, "image/jpeg")).actions().andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.postId").value(5));
		create(photos(11, 1000, "image/jpeg")).actions().andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("media"));
		create(photos(0, 1000, "image/jpeg")).actions().andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("media"));
	}

	@Test
	void jpeg가_아니거나_15MB를_넘으면_검증_실패다() throws Exception {
		// given
		given(postCommandService.createDraft(eq(1L), any())).willReturn(new PostDraftResponse(5L, List.of()));

		// when / then
		create(photos(1, 15_728_640L, "image/jpeg")).actions().andExpect(status().isCreated());
		create(photos(1, 15_728_641L, "image/jpeg")).actions().andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		create(photos(1, 0, "image/jpeg")).actions().andExpect(status().isBadRequest());
		create(photos(1, 1000, "image/png")).actions().andExpect(status().isBadRequest());
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON)
				.content(photos(1, 1000, "image/jpeg"))).andExpect(status().isUnauthorized());
		verify(postCommandService, never()).createDraft(anyLong(), any());
	}

	@Test
	void 게시_본문은_2200자까지다() throws Exception {
		// given
		String bearer = bearer(1L, Role.USER);
		given(postCommandService.publish(eq(1L), eq(3L), any())).willReturn(response());

		// when / then
		mockMvc.perform(post("/api/v1/posts/3/publish").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + "가".repeat(2200) + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
		mockMvc.perform(post("/api/v1/posts/3/publish").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + "가".repeat(2201) + "\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("body"));
	}

	@Test
	void 수정은_200이고_삭제는_204다() throws Exception {
		// given
		String bearer = bearer(1L, Role.MEMBER);
		given(postCommandService.update(eq(1L), eq(3L), eq("새 본문"))).willReturn(response());

		// when / then
		mockMvc.perform(patch("/api/v1/posts/3").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"새 본문\"}")).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/posts/3").header("Authorization", bearer)).andExpect(status().isNoContent());
		verify(postCommandService).delete(1L, Role.MEMBER, 3L);
	}

	private PostResponse response() {
		return new PostResponse(3L, new PostAuthorResponse(1L, "climber"), "", List.of(), 0, 0, null, "PENDING", true);
	}
}
