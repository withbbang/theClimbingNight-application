package com.theclimbingnight.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTokenVerifierTest {

	private MockRestServiceServer server;
	private KakaoTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		verifier = new KakaoTokenVerifier(builder.build(), SocialTestSupport.props(List.of(), List.of()));
	}

	@Test
	void 응답의_id를_사용자ID로_돌려준다() {
		// given
		server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer tok"))
				.andRespond(withSuccess("{\"id\":1234567,\"expires_in\":3600,\"app_id\":12345}", MediaType.APPLICATION_JSON));

		// when
		SocialIdentity identity = verifier.verify("tok");

		// then
		assertThat(identity).isEqualTo(new SocialIdentity(SocialProvider.KAKAO, "1234567"));
		server.verify();
	}

	private BusinessException verifyWith(String kakaoAppId, String body) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
		KakaoTokenVerifier custom = new KakaoTokenVerifier(builder.build(),
				SocialTestSupport.props(List.of(), List.of(), kakaoAppId));
		mock.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
		return catchThrowableOfType(BusinessException.class, () -> custom.verify("tok"));
	}

	@Test
	void app_id가_다른_앱의_토큰이면_AUTH_INVALID_SOCIAL_TOKEN이다() {
		// given, when
		BusinessException e = verifyWith("12345", "{\"id\":1,\"expires_in\":3600,\"app_id\":99999}");

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 설정한_앱ID가_비어_있으면_AUTH_INVALID_SOCIAL_TOKEN이다() {
		// given, when
		BusinessException e = verifyWith("", "{\"id\":1,\"expires_in\":3600,\"app_id\":12345}");

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 응답에_app_id가_없으면_AUTH_INVALID_SOCIAL_TOKEN이다() {
		// given, when
		BusinessException e = verifyWith("12345", "{\"id\":1,\"expires_in\":3600}");

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 제공자가_401이면_AUTH_INVALID_SOCIAL_TOKEN이다() {
		// given
		server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier.verify("tok"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

	@Test
	void 제공자가_500이면_AUTH_PROVIDER_UNAVAILABLE이다() {
		// given
		server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info")).andRespond(withServerError());

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier.verify("tok"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
	}

}
