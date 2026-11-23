package com.theclimbingnight.roster.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.theclimbingnight.roster.dto.RosterImportResponse;
import com.theclimbingnight.roster.dto.RosterMemberResponse;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.service.RosterImportService;
import com.theclimbingnight.roster.service.RosterQueryService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RosterMemberController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class RosterMemberControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	RosterImportService rosterImportService;

	@MockitoBean
	RosterQueryService rosterQueryService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private MockMultipartFile file() {
		return new MockMultipartFile("file", "roster.csv", "text/csv", "소모임닉네임\n철수\n".getBytes());
	}

	@Test
	void USER가_올리면_403이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.USER);

		// when, then
		mockMvc.perform(multipart("/api/v1/roster-members/import").file(file()).header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@Test
	void 파일_파트가_없으면_400이다() throws Exception {
		// given
		String bearer = bearer(2L, Role.ADMIN);

		// when, then
		mockMvc.perform(multipart("/api/v1/roster-members/import").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void ADMIN이_올리면_결과를_돌려준다() throws Exception {
		// given
		String bearer = bearer(2L, Role.ADMIN);
		given(rosterImportService.importCsv(any()))
				.willReturn(new RosterImportResponse(3, List.of(), List.of()));

		// when, then
		mockMvc.perform(multipart("/api/v1/roster-members/import").file(file()).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.addedCount").value(3));
	}

	@Test
	void ADMIN이_명부를_검색하면_페이지로_돌려준다() throws Exception {
		// given
		String bearer = bearer(2L, Role.ADMIN);
		given(rosterQueryService.search("철수", false, 0, 20)).willReturn(new PageResponse<>(
				List.of(new RosterMemberResponse(11L, "박철수", null, RosterSource.IMPORT, null)), 0, 20, 1, 1));

		// when, then
		mockMvc.perform(get("/api/v1/roster-members?q=철수&linked=false").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].somoimNickname").value("박철수"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void 명부_검색의_size가_101이면_400이고_USER면_403이다() throws Exception {
		// given
		String admin = bearer(2L, Role.ADMIN);
		String user = bearer(3L, Role.USER);

		// when, then
		mockMvc.perform(get("/api/v1/roster-members?size=101").header("Authorization", admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/roster-members").header("Authorization", user))
				.andExpect(status().isForbidden());
	}

}
