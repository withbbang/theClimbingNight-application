package com.theclimbingnight.feed.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.feed.dto.LikeResponse;
import com.theclimbingnight.feed.dto.LikerResponse;
import com.theclimbingnight.feed.service.PostLikeService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostLikeController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class PostLikeControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean PostLikeService postLikeService;

	private String bearer(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", Role.USER);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		mockMvc.perform(put("/api/v1/posts/5/like")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
		mockMvc.perform(delete("/api/v1/posts/5/like")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/posts/5/likes")).andExpect(status().isUnauthorized());
	}

	@Test
	void 켜기는_PUT이고_LikeResponse를_준다() throws Exception {
		// given
		String bearer = bearer(1L);
		given(postLikeService.like(1L, 5L)).willReturn(new LikeResponse(4, true));
		given(postLikeService.unlike(1L, 5L)).willReturn(new LikeResponse(3, false));

		// when / then
		mockMvc.perform(put("/api/v1/posts/5/like").header("Authorization", bearer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(4)).andExpect(jsonPath("$.data.likedByMe").value(true));
		mockMvc.perform(delete("/api/v1/posts/5/like").header("Authorization", bearer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(3)).andExpect(jsonPath("$.data.likedByMe").value(false));
	}

	@Test
	void 누른_사람_목록은_기본_size_20이다() throws Exception {
		// given
		String bearer = bearer(1L);
		given(postLikeService.likers(eq(1L), eq(5L), isNull(), eq(20)))
				.willReturn(new CursorResponse<>(List.of(new LikerResponse(7L, "민수")), "next"));

		// when / then
		mockMvc.perform(get("/api/v1/posts/5/likes").header("Authorization", bearer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].nickname").value("민수"))
				.andExpect(jsonPath("$.data.nextCursor").value("next"));
	}

	@Test
	void size가_50을_넘으면_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts/5/likes").param("size", "51").header("Authorization", bearer(1L)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}
}
