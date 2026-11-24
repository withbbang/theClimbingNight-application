package com.theclimbingnight.meetup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
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
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.meetup.dto.MeetupResponse;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.entity.MeetupStatus;
import com.theclimbingnight.meetup.service.MeetupAdminService;
import com.theclimbingnight.meetup.service.MeetupQueryService;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
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

@WebMvcTest(MeetupController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class MeetupControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	MeetupAdminService meetupAdminService;

	@MockitoBean
	MeetupQueryService meetupQueryService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	private String body(String startsAt, String endsAt, int capacity) {
		return "{\"title\":\"정기벙\",\"placeName\":\"더클라임\",\"startsAt\":\"" + startsAt + "\",\"endsAt\":\""
				+ endsAt + "\",\"capacity\":" + capacity + "}";
	}

	private String validBody() {
		return body("2026-12-01T19:00:00+09:00", "2026-12-01T22:00:00+09:00", 10);
	}

	private MeetupResponse response() {
		Instant start = Instant.parse("2026-12-01T10:00:00Z");
		return new MeetupResponse(5L, "정기벙", null, "더클라임", start, start.plusSeconds(10800), 10, 24, true,
				MeetupStatus.OPEN, null, null);
	}

	@Test
	void MEMBER가_벙을_개설하면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(post("/api/v1/meetups").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@Test
	void ADMIN이_개설하면_201이고_기본값이_채워진다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		given(meetupAdminService.create(anyLong(), any(MeetupSpec.class))).willReturn(response());

		// when, then
		mockMvc.perform(post("/api/v1/meetups").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(5));
		verify(meetupAdminService).create(1L, new MeetupSpec("정기벙", null, "더클라임",
				Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T13:00:00Z"), 10, 24, true));
	}

	@Test
	void 종료가_시작보다_빠르거나_24시간을_넘으면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);

		// when, then
		for (String[] times : new String[][] {
				{"2026-12-01T19:00:00+09:00", "2026-12-01T18:00:00+09:00"},
				{"2026-12-01T19:00:00+09:00", "2026-12-01T19:00:00+09:00"},
				{"2026-12-01T19:00:00+09:00", "2026-12-02T19:00:01+09:00"}}) {
			mockMvc.perform(post("/api/v1/meetups").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content(body(times[0], times[1], 10)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
	}

	@Test
	void 정원이_0이나_501이면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);

		// when, then
		for (int capacity : new int[] {0, 501}) {
			mockMvc.perform(post("/api/v1/meetups").header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("2026-12-01T19:00:00+09:00", "2026-12-01T22:00:00+09:00", capacity)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
	}

	@Test
	void 오프셋_없는_시각은_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);

		// when, then
		mockMvc.perform(post("/api/v1/meetups").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("2026-12-01T19:00:00", "2026-12-01T22:00:00", 10)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 수정은_정원을_빼면_400이다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		String noCapacity = "{\"title\":\"정기벙\",\"placeName\":\"더클라임\",\"startsAt\":\"2026-12-01T19:00:00+09:00\","
				+ "\"endsAt\":\"2026-12-01T22:00:00+09:00\",\"lateCancelHours\":24,\"participantsVisible\":true}";

		// when, then
		mockMvc.perform(put("/api/v1/meetups/5").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(noCapacity))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 취소와_복제는_ADMIN만_하고_복제는_201이다() throws Exception {
		// given
		String admin = bearer(1L, Role.ADMIN);
		String member = bearer(7L, Role.MEMBER);
		given(meetupAdminService.cancel(5L)).willReturn(response());
		given(meetupAdminService.copy(5L, 1L, Instant.parse("2026-12-08T10:00:00Z"),
				Instant.parse("2026-12-08T13:00:00Z"))).willReturn(response());
		String copyBody = "{\"startsAt\":\"2026-12-08T19:00:00+09:00\",\"endsAt\":\"2026-12-08T22:00:00+09:00\"}";

		// when, then
		mockMvc.perform(post("/api/v1/meetups/5/cancel").header("Authorization", member))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/meetups/5/cancel").header("Authorization", admin))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/meetups/5/copy").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content(copyBody))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/meetups/5/copy").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startsAt\":\"2026-12-08T22:00:00+09:00\",\"endsAt\":\"2026-12-08T19:00:00+09:00\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void USER가_벙_목록을_보면_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.USER);

		// when, then
		mockMvc.perform(get("/api/v1/meetups").param("from", "2026-12-01").param("to", "2026-12-31")
				.header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@Test
	void 날짜_형식이_틀리면_400이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/meetups").param("from", "2026/12/01").param("to", "2026-12-31")
				.header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 날짜가_없으면_400이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/meetups").param("from", "2026-12-01").header("Authorization", bearer))
				.andExpect(status().isBadRequest());
	}

	@Test
	void MEMBER가_목록을_보면_200이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		given(meetupQueryService.list(7L, java.time.LocalDate.of(2026, 12, 1), java.time.LocalDate.of(2026, 12, 31)))
				.willReturn(java.util.List.of());

		// when, then
		mockMvc.perform(get("/api/v1/meetups").param("from", "2026-12-01").param("to", "2026-12-31")
				.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void 상세는_호출자의_역할을_넘긴다() throws Exception {
		// given
		String bearer = bearer(1L, Role.ADMIN);
		given(meetupQueryService.get(5L, 1L, Role.ADMIN)).willReturn(
				new com.theclimbingnight.meetup.dto.MeetupDetailResponse(response(), 0, 0, null, null));

		// when, then
		mockMvc.perform(get("/api/v1/meetups/5").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetup.id").value(5));
	}
}
