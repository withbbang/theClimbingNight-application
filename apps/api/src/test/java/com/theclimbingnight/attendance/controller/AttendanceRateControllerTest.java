package com.theclimbingnight.attendance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.attendance.dto.AttendanceRateResponse;
import com.theclimbingnight.attendance.service.AttendancePeriod;
import com.theclimbingnight.attendance.service.AttendanceRateService;
import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.GlobalExceptionHandler;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceRateController.class)
@Import({WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class, JwtTokenService.class,
		ClockConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AuthProperties.class)
class AttendanceRateControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenService jwtTokenService;

	@MockitoBean
	UserRepository userRepository;

	@MockitoBean
	AttendanceRateService attendanceRateService;

	private String bearer(long id, Role role) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "role", role);
		given(userRepository.findById(id)).willReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issueAccessToken(id).token();
	}

	@Test
	void 일반회원은_403이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.USER);

		// when, then
		mockMvc.perform(get("/api/v1/attendance-rates/me?period=MONTH").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		verify(attendanceRateService, never()).mine(anyLong(), any(), any());
	}

	@Test
	void MEMBER는_내_참석률을_본다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);
		given(attendanceRateService.mine(7L, AttendancePeriod.MONTH, LocalDate.of(2026, 11, 24)))
				.willReturn(new AttendanceRateResponse(AttendancePeriod.MONTH, LocalDate.of(2026, 11, 1),
						LocalDate.of(2026, 11, 30), 2, 3, new BigDecimal("66.7")));

		// when, then
		mockMvc.perform(get("/api/v1/attendance-rates/me?period=MONTH&date=2026-11-24")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.period").value("MONTH"))
				.andExpect(jsonPath("$.data.attendedCount").value(2))
				.andExpect(jsonPath("$.data.rate").value(66.7));
	}

	@Test
	void 없는_기간이면_400이다() throws Exception {
		// given
		String bearer = bearer(7L, Role.MEMBER);

		// when, then
		mockMvc.perform(get("/api/v1/attendance-rates/me?period=DECADE").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}
}
