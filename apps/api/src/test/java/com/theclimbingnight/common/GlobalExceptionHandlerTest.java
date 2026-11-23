package com.theclimbingnight.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.auth.web.AuthInterceptor;
import com.theclimbingnight.auth.web.CurrentUserArgumentResolver;
import com.theclimbingnight.common.config.ClockConfig;
import com.theclimbingnight.common.config.WebConfig;
import com.theclimbingnight.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ThrowingController.class)
// WebMvcConfigurer(WebConfig)가 슬라이스에 포함되므로 인증 빈 의존성을 채워 준다.
@Import({GlobalExceptionHandler.class, WebConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class,
		JwtTokenService.class, ClockConfig.class})
@EnableConfigurationProperties(AuthProperties.class)
class GlobalExceptionHandlerTest {

	@MockitoBean
	UserRepository userRepository;

	@Autowired
	MockMvc mockMvc;

	@Test
	void 비즈니스_예외는_에러코드의_상태와_메시지로_응답한다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/test/forbidden"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"))
				.andExpect(jsonPath("$.error.message").value("권한이 없습니다."));
	}

	@Test
	void 검증_실패는_필드별_사유를_담는다() throws Exception {
		// given
		String body = "{\"name\":\"\"}";

		// when, then
		mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("name"));
	}

	@Test
	void 없는_경로는_404_COMMON_NOT_FOUND다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/test/none"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void 지원하지_않는_메서드는_405_COMMON_METHOD_NOT_ALLOWED다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/test/valid"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("COMMON_METHOD_NOT_ALLOWED"))
				.andExpect(jsonPath("$.error.message").value("지원하지 않는 요청 방식입니다."));
	}

	@Test
	void 지원하지_않는_Content_Type은_415_COMMON_UNSUPPORTED_MEDIA_TYPE이다() throws Exception {
		// given, when, then
		mockMvc.perform(post("/test/valid").contentType(MediaType.TEXT_PLAIN).content("name"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"))
				.andExpect(jsonPath("$.error.message").value("지원하지 않는 요청 형식입니다."));
	}

	@Test
	void 처리되지_않은_예외는_500이고_내부_메시지를_노출하지_않는다() throws Exception {
		// given, when
		String response = mockMvc.perform(get("/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
				.andReturn().getResponse().getContentAsString();

		// then
		assertThat(response).doesNotContain("secret");
	}

}
