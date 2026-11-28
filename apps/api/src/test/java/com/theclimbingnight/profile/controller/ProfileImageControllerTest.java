package com.theclimbingnight.profile.controller;

import static org.mockito.ArgumentMatchers.anyLong;
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
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.profile.dto.ProfileImageStatusResponse;
import com.theclimbingnight.profile.dto.ProfileImageUploadResponse;
import com.theclimbingnight.profile.service.ProfileImageService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
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

@WebMvcTest(ProfileImageController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class ProfileImageControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenService jwtTokenService;

	@MockitoBean UserRepository userRepository;
	@MockitoBean ProfileImageService service;

	private String bearer(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", Role.USER);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/users/me/profile-images").contentType(MediaType.APPLICATION_JSON)
				.content("{\"byteSize\":10}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 시작하면_201과_업로드_주소를_준다() throws Exception {
		given(service.start(1L, 1000L)).willReturn(new ProfileImageUploadResponse(2L, "https://r2/put",
				Map.of("Content-Type", "image/jpeg"), OffsetDateTime.parse("2026-11-28T12:15:00Z")));

		mockMvc.perform(post("/api/v1/users/me/profile-images").header("Authorization", bearer(1L))
				.contentType(MediaType.APPLICATION_JSON).content("{\"byteSize\":1000}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(2))
				.andExpect(jsonPath("$.data.url").value("https://r2/put"));
	}

	@Test
	void byteSize가_범위를_벗어나면_400이다() throws Exception {
		for (String body : new String[] { "{\"byteSize\":0}", "{\"byteSize\":5242881}" }) {
			mockMvc.perform(post("/api/v1/users/me/profile-images").header("Authorization", bearer(1L))
					.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
	}

	@Test
	void 확정하면_202다() throws Exception {
		given(service.confirm(1L, 2L)).willReturn(new ProfileImageStatusResponse(2L, "PROCESSING"));

		mockMvc.perform(post("/api/v1/users/me/profile-images/2/confirm").header("Authorization", bearer(1L)))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PROCESSING"));
	}

	@Test
	void 상태를_조회한다() throws Exception {
		given(service.status(1L, 2L)).willReturn(new ProfileImageStatusResponse(2L, "READY"));

		mockMvc.perform(get("/api/v1/users/me/profile-images/2").header("Authorization", bearer(1L)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"));
	}

	@Test
	void 되돌리면_204다() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me/profile-image").header("Authorization", bearer(1L)))
				.andExpect(status().isNoContent());
	}
}
