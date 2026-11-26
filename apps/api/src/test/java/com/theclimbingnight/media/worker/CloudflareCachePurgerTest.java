package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CloudflareCachePurgerTest {

	private static final String URL = "https://api.cloudflare.com/client/v4/zones/zone-1/purge_cache";
	private static final List<String> FILES = List.of("https://m.example/p/k/d.webp", "https://m.example/p/k/t.webp");

	private MockRestServiceServer server;
	private CloudflareCachePurger purger;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		purger = new CloudflareCachePurger(builder.build(), "zone-1", "secret-token");
	}

	@Test
	void 파일_목록을_Bearer_토큰으로_보낸다() {
		// given
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer secret-token"))
				.andExpect(jsonPath("$.files.length()").value(2))
				.andExpect(jsonPath("$.files[0]").value(FILES.get(0)))
				.andExpect(jsonPath("$.files[1]").value(FILES.get(1)))
				.andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

		// when
		purger.purge(FILES);

		// then
		server.verify();
	}

	@Test
	void 응답이_500이면_예외이고_토큰을_담지_않는다() {
		// given
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("{\"errors\":[{\"message\":\"secret-token\"}]}").contentType(MediaType.APPLICATION_JSON));

		// when
		Throwable thrown = catchThrowable(() -> purger.purge(FILES));

		// then
		assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("500");
		assertThat(thrown.getMessage()).doesNotContain("secret-token");
		assertThat(thrown.getCause()).isNull();
	}

	@Test
	void 빈_목록이면_요청하지_않는다() {
		// when
		purger.purge(List.of());

		// then
		server.verify();
	}
}
