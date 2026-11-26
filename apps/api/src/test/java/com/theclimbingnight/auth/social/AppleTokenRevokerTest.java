package com.theclimbingnight.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleTokenRevokerTest {

	private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
	private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

	private MockRestServiceServer server;
	private AppleTokenRevoker revoker;

	@BeforeEach
	void setUp() {
		revoker = revokerWith(SocialTestSupport.appleRevokeProps("TEAM123456", "KEY1234567",
				SocialTestSupport.pemOf(SocialTestSupport.newEcKey())));
	}

	private AppleTokenRevoker revokerWith(AuthProperties props) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		MutableClock clock = new MutableClock(Instant.parse("2026-11-26T00:00:00Z"));
		return new AppleTokenRevoker(builder.build(), props, new AppleClientSecret(props, clock));
	}

	private static String tokenResponse(String sub) {
		return "{\"access_token\":\"a1\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
				+ "\"refresh_token\":\"r1\",\"id_token\":\"" + SocialTestSupport.idTokenWithSubject(sub) + "\"}";
	}

	@Test
	void code를_교환해_refresh_token을_돌려준다() {
		// given
		server.expect(requestTo(TOKEN_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(java.util.Map.of(
						"grant_type", "authorization_code", "code", "c1", "client_id", "com.theclimbingnight.app")))
				.andRespond(withSuccess(tokenResponse("apple-user"), MediaType.APPLICATION_JSON));

		// when
		String refreshToken = revoker.exchange("c1", "apple-user");

		// then
		assertThat(refreshToken).isEqualTo("r1");
		server.verify();
	}

	@Test
	void id_token의_sub가_다르면_재인증을_요구한다() {
		// given
		server.expect(requestTo(TOKEN_URL))
				.andRespond(withSuccess(tokenResponse("someone-else"), MediaType.APPLICATION_JSON));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class,
				() -> revoker.exchange("c1", "apple-user"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
	}

	@Test
	void Apple이_400이면_재인증을_요구한다() {
		// given
		server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class,
				() -> revoker.exchange("c1", "apple-user"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
	}

	@Test
	void 우리_키_설정이_틀려_invalid_client면_재인증이_아니라_제공자_장애다() {
		// given: 재인증을 요구하면 같은 오류가 반복되어 사용자가 탈퇴를 끝낼 수 없다
		server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class,
				() -> revoker.exchange("c1", "apple-user"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
	}

	@Test
	void Apple이_500이면_제공자_장애다() {
		// given
		server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class,
				() -> revoker.exchange("c1", "apple-user"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
	}

	@Test
	void revoke는_refresh_token을_폐기_요청한다() {
		// given
		server.expect(requestTo(REVOKE_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(java.util.Map.of(
						"token", "r1", "token_type_hint", "refresh_token", "client_id", "com.theclimbingnight.app")))
				.andRespond(withSuccess());

		// when
		revoker.revoke("r1");

		// then
		server.verify();
	}

	@Test
	void revoke가_실패해도_예외를_던지지_않는다() {
		// given
		server.expect(requestTo(REVOKE_URL)).andRespond(withServerError());

		// when, then
		assertThatCode(() -> revoker.revoke("r1")).doesNotThrowAnyException();
	}

	@Test
	void 키가_없으면_enabled는_false다() {
		// given
		AppleTokenRevoker off = revokerWith(SocialTestSupport.appleRevokeProps("TEAM123456", "KEY1234567", ""));

		// then
		assertThat(off.enabled()).isFalse();
		assertThat(revoker.enabled()).isTrue();
	}

}
