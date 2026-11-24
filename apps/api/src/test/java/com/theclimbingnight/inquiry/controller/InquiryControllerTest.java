package com.theclimbingnight.inquiry.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.theclimbingnight.inquiry.dto.InquiryAdminResponse;
import com.theclimbingnight.inquiry.dto.InquiryAnswerRequest;
import com.theclimbingnight.inquiry.dto.InquiryCreateRequest;
import com.theclimbingnight.inquiry.dto.InquiryResponse;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import com.theclimbingnight.inquiry.service.InquiryService;
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

@WebMvcTest(InquiryController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class InquiryControllerTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	InquiryService inquiryService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private static String json(String title, String body) {
		return "{\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
	}

	private static InquiryResponse response() {
		return new InquiryResponse(1, "t", "b", InquiryStatus.OPEN, null, null, NOW);
	}

	@Test
	void 일반회원이_문의를_남긴다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);
		given(inquiryService.create(anyLong(), any(InquiryCreateRequest.class))).willReturn(response());

		// when, then
		mockMvc.perform(post("/api/v1/inquiries").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json("t", "b")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("OPEN"));
	}

	@Test
	void 내_문의_목록과_상세를_본다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);
		given(inquiryService.mine(3L, 0, 20)).willReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1));
		given(inquiryService.getMine(3L, 1L)).willReturn(response());

		// when, then
		mockMvc.perform(get("/api/v1/inquiries/me").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(1));
		mockMvc.perform(get("/api/v1/inquiries/me/1").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("t"));
	}

	@Test
	void 동호회원이_전체_목록을_보면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/inquiries").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		mockMvc.perform(put("/api/v1/inquiries/1/answer").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"a\"}"))
				.andExpect(status().isForbidden());
		verify(inquiryService, never()).answer(anyLong(), anyLong(), any(InquiryAnswerRequest.class));
	}

	@Test
	void 운영진이_상태로_목록을_보고_답변한다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		InquiryAdminResponse admin = new InquiryAdminResponse(1, "t", "b", InquiryStatus.ANSWERED, "a", NOW, NOW, 3L,
				"climber");
		given(inquiryService.list(InquiryStatus.OPEN, 0, 20))
				.willReturn(new PageResponse<>(List.of(admin), 0, 20, 1, 1));
		given(inquiryService.answer(anyLong(), anyLong(), any(InquiryAnswerRequest.class))).willReturn(admin);

		// when, then
		mockMvc.perform(get("/api/v1/inquiries?status=OPEN").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].nickname").value("climber"));
		mockMvc.perform(put("/api/v1/inquiries/1/answer").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"a\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.answer").value("a"));
	}

	@Test
	void 본문은_이천_자까지다() throws Exception {
		// given
		String bearer = bearer(3L, Role.USER);
		given(inquiryService.create(anyLong(), any(InquiryCreateRequest.class))).willReturn(response());

		// when, then
		mockMvc.perform(post("/api/v1/inquiries").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json("t", "a".repeat(2000))))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/inquiries").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json("t", "a".repeat(2001))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}
}
