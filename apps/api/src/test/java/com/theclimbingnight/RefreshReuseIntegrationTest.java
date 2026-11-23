package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.auth.social.SocialTokenVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 시계를 조작해야 하므로 AuthFlowIntegrationTest와 별도 컨텍스트로 둔다. 테스트에 트랜잭션을 걸지 않아 실제 커밋을 확인한다. */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
class RefreshReuseIntegrationTest {

	static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-03-01T00:00:00Z"));

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		Clock testClock() {
			return CLOCK;
		}

		@Bean
		SocialTokenVerifier kakaoTokenVerifier() {
			return new SocialTokenVerifier() {
				@Override
				public SocialProvider provider() {
					return SocialProvider.KAKAO;
				}

				@Override
				public SocialIdentity verify(String token) {
					return new SocialIdentity(SocialProvider.KAKAO, token);
				}
			};
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private JsonNode json(ResultActions actions) throws Exception {
		return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private String signupAndGetRefresh(String social, String nickname) throws Exception {
		JsonNode first = json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\":\"KAKAO\",\"token\":\"" + social + "\"}")).andExpect(status().isOk()));
		return json(mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"signupToken\":\"" + first.at("/data/signupToken").asText() + "\",\"nickname\":\""
						+ nickname + "\"}")).andExpect(status().isCreated())).at("/data/refreshToken").asText();
	}

	@Test
	void 유예_시간이_지난_뒤_폐기된_토큰을_재사용하면_다른_활성_토큰도_DB에서_폐기된다() throws Exception {
		// given: 리프레시로 R1 -> R2 회전
		String r1 = signupAndGetRefresh("reuse-1", "재사용A");
		String r2 = json(refresh(r1).andExpect(status().isOk())).at("/data/refreshToken").asText();

		// when: 유예(30초)가 지난 뒤 폐기된 R1을 다시 보낸다
		CLOCK.advance(Duration.ofSeconds(31));
		refresh(r1).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));

		// then: 예외로 끝난 요청이어도 전체 폐기가 커밋돼 R2도 거절된다
		refresh(r2).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 유예_시간_안에_폐기된_토큰을_재사용하면_다른_활성_토큰은_살아_있다() throws Exception {
		// given
		String r1 = signupAndGetRefresh("reuse-2", "재사용B");
		String r2 = json(refresh(r1).andExpect(status().isOk())).at("/data/refreshToken").asText();

		// when
		CLOCK.advance(Duration.ofSeconds(5));
		refresh(r1).andExpect(status().isUnauthorized());

		// then
		String r3 = json(refresh(r2).andExpect(status().isOk())).at("/data/refreshToken").asText();
		assertThat(r3).isNotBlank();
	}

}
