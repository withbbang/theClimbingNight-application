package com.theclimbingnight.moderation.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.moderation.dto.BlockedUserResponse;
import com.theclimbingnight.moderation.service.BlockService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BlockController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class BlockControllerTest {

	private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-26T12:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean BlockService blockService;

	private String bearer(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", Role.USER);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 차단은_두_번_해도_201이다() throws Exception {
		// given
		String bearer = bearer(3L);
		given(blockService.block(3L, 9L)).willReturn(new BlockedUserResponse(9L, "대상", AT));

		// when / then
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/blocks").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":9}"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.userId").value(9))
					.andExpect(jsonPath("$.data.nickname").value("대상"));
		}
		verify(blockService, times(2)).block(3L, 9L);
	}

	@Test
	void 자기_자신_차단은_400_BLOCK_SELF다() throws Exception {
		// given
		String bearer = bearer(3L);
		given(blockService.block(3L, 3L)).willThrow(new BusinessException(ErrorCode.BLOCK_SELF));

		// when / then
		mockMvc.perform(post("/api/v1/blocks").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":3}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("BLOCK_SELF"));
	}

	@Test
	void 차단_해제는_204고_목록은_배열이다() throws Exception {
		// given
		String bearer = bearer(3L);
		given(blockService.list(3L)).willReturn(List.of(new BlockedUserResponse(9L, "대상", AT)));

		// when / then
		mockMvc.perform(delete("/api/v1/blocks/9").header("Authorization", bearer))
				.andExpect(status().isNoContent());
		verify(blockService).unblock(3L, 9L);
		mockMvc.perform(get("/api/v1/blocks").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].userId").value(9))
				.andExpect(jsonPath("$.data[0].blockedAt").exists());
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/blocks").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":9}"))
				.andExpect(status().isUnauthorized());
		verify(blockService, never()).block(anyLong(), anyLong());
	}
}
