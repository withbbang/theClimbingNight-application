package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.auth.social.SocialTokenVerifier;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

	/** 소셜 검증기가 호출된 순간 DB 트랜잭션이 열려 있었는지 기록한다. */
	static final AtomicBoolean TX_ACTIVE_DURING_VERIFY = new AtomicBoolean(true);

	@TestConfiguration
	static class FakeKakaoConfig {

		/** 토큰 문자열을 그대로 제공자 사용자 ID로 쓰는 검증기. 실제 Kakao 검증기를 같은 이름으로 대체한다. */
		@Bean
		SocialTokenVerifier kakaoTokenVerifier() {
			return new SocialTokenVerifier() {
				@Override
				public SocialProvider provider() {
					return SocialProvider.KAKAO;
				}

				@Override
				public SocialIdentity verify(String token) {
					TX_ACTIVE_DURING_VERIFY.set(TransactionSynchronizationManager.isActualTransactionActive());
					return new SocialIdentity(SocialProvider.KAKAO, token);
				}
			};
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}

	private String login(String socialToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"KAKAO\",\"token\":\"" + socialToken + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private JsonNode signup(String signupToken, String nickname) throws Exception {
		return json(mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"signupToken\":\"" + signupToken + "\",\"nickname\":\"" + nickname + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	void 가입부터_탈퇴_후_재가입까지() throws Exception {
		// given: 처음 로그인하면 가입이 필요하다
		JsonNode first = json(login("u1"));
		assertThat(first.at("/data/status").asText()).isEqualTo("SIGNUP_REQUIRED");

		// when: 가입하고 내 정보를 조회한다
		JsonNode signedUp = signup(first.at("/data/signupToken").asText(), "클라이머");
		String access = signedUp.at("/data/accessToken").asText();
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("클라이머"))
				.andExpect(jsonPath("$.data.role").value("USER"));

		// when: 닉네임을 바꿨다가 되돌린다
		mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"볼더러\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("볼더러"));
		mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"클라이머\"}"))
				.andExpect(status().isOk());

		// when: 탈퇴하면 같은 액세스 토큰은 더 이상 쓸 수 없다
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));

		// then: 같은 소셜 계정은 새 계정으로 가입해야 하고, 탈퇴자의 닉네임은 다시 쓸 수 있다
		JsonNode again = json(login("u1"));
		assertThat(again.at("/data/status").asText()).isEqualTo("SIGNUP_REQUIRED");
		JsonNode reSignedUp = signup(again.at("/data/signupToken").asText(), "클라이머");
		assertThat(reSignedUp.at("/data/user/nickname").asText()).isEqualTo("클라이머");
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	@Test
	void 리프레시하면_새_토큰을_받고_이전_토큰은_거절된다() throws Exception {
		// given
		JsonNode first = json(login("u2"));
		JsonNode signedUp = signup(first.at("/data/signupToken").asText(), "회전왕");
		String oldRefresh = signedUp.at("/data/refreshToken").asText();

		// when
		JsonNode rotated = json(refresh(oldRefresh).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		// then
		String newRefresh = rotated.at("/data/refreshToken").asText();
		assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);
		refresh(oldRefresh).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
		refresh(newRefresh).andExpect(status().isOk());
	}

	@Test
	void 탈퇴_전에_발급된_리프레시_토큰은_탈퇴_후_거절된다() throws Exception {
		// given
		JsonNode first = json(login("u3"));
		JsonNode signedUp = signup(first.at("/data/signupToken").asText(), "탈퇴예정");
		String access = signedUp.at("/data/accessToken").asText();
		String refreshToken = signedUp.at("/data/refreshToken").asText();

		// when
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isNoContent());

		// then
		refresh(refreshToken).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 탈퇴_요청에_appleAuthorizationCode를_실어도_카카오_사용자는_탈퇴된다() throws Exception {
		// given
		JsonNode first = json(login("u4"));
		String access = signup(first.at("/data/signupToken").asText(), "본문탈퇴").at("/data/accessToken").asText();

		// when, then
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON).content("{\"appleAuthorizationCode\":\"c1\"}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 소셜_검증은_DB_트랜잭션_밖에서_호출된다() throws Exception {
		// given
		TX_ACTIVE_DURING_VERIFY.set(true);

		// when
		login("tx-check");

		// then
		assertThat(TX_ACTIVE_DURING_VERIFY.get()).isFalse();
	}

	@Test
	void 내_정보는_로그인_없이_조회할_수_없다() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

}
