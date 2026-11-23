package com.theclimbingnight.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverTokenVerifierTest {

	private MockRestServiceServer server;
	private NaverTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		verifier = new NaverTokenVerifier(builder.build(), SocialTestSupport.props(List.of(), List.of()));
	}

	@Test
	void resultcode가_00이면_response_id를_돌려준다() {
		// given
		server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
				.andExpect(header("Authorization", "Bearer tok"))
				.andRespond(withSuccess("{\"resultcode\":\"00\",\"message\":\"success\",\"response\":{\"id\":\"abc123\"}}",
						MediaType.APPLICATION_JSON));

		// when
		SocialIdentity identity = verifier.verify("tok");

		// then
		assertThat(identity).isEqualTo(new SocialIdentity(SocialProvider.NAVER, "abc123"));
	}

	@Test
	void resultcode가_00이_아니면_AUTH_INVALID_SOCIAL_TOKEN이다() {
		// given
		server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
				.andRespond(withSuccess("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}",
						MediaType.APPLICATION_JSON));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> verifier.verify("tok"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
	}

}
