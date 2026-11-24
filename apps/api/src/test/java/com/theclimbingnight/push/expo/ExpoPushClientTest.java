package com.theclimbingnight.push.expo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.theclimbingnight.push.PushProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExpoPushClientTest {

	private static final String SEND = "https://exp.host/--/api/v2/push/send";
	private static final String RECEIPTS = "https://exp.host/--/api/v2/push/getReceipts";

	private MockRestServiceServer server;

	private ExpoPushClient client(String accessToken) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return new ExpoPushClient(builder.build(), new PushProperties(true, "https://exp.host", accessToken));
	}

	private static PushMessage message(String to) {
		return new PushMessage(to, "제목", "본문", Map.of("type", "NOTICE"));
	}

	@Test
	void 메시지_배열을_보내고_티켓을_순서대로_돌려준다() {
		// given
		ExpoPushClient client = client("");
		server.expect(requestTo(SEND))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].to").value("ExponentPushToken[a]"))
				.andExpect(jsonPath("$[0].title").value("제목"))
				.andExpect(jsonPath("$[0].body").value("본문"))
				.andExpect(jsonPath("$[0].data.type").value("NOTICE"))
				.andExpect(jsonPath("$[0].sound").value("default"))
				.andExpect(jsonPath("$[0].channelId").value("default"))
				.andExpect(jsonPath("$[0].priority").value("high"))
				.andExpect(jsonPath("$[1].to").value("ExponentPushToken[b]"))
				.andExpect(jsonPath("$[1].priority").value("high"))
				.andRespond(withSuccess("{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"},{\"status\":\"ok\",\"id\":\"t2\"}]}",
						MediaType.APPLICATION_JSON));

		// when
		List<PushTicketResult> results = client.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]")));

		// then
		assertThat(results).containsExactly(new PushTicketResult("t1", null), new PushTicketResult("t2", null));
		server.verify();
	}

	@Test
	void 토큰이_있으면_인증_헤더를_붙이고_없으면_뺀다() {
		// given
		String body = "{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"}]}";
		ExpoPushClient withToken = client("secret");
		server.expect(requestTo(SEND)).andExpect(header("Authorization", "Bearer secret"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		// when
		withToken.send(List.of(message("ExponentPushToken[a]")));

		// then
		server.verify();

		// given
		ExpoPushClient without = client("");
		server.expect(requestTo(SEND)).andExpect(headerDoesNotExist("Authorization"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		// when
		without.send(List.of(message("ExponentPushToken[a]")));

		// then
		server.verify();
	}

	@Test
	void 오류_티켓은_details의_error를_담는다() {
		// given
		ExpoPushClient client = client("");
		server.expect(requestTo(SEND)).andRespond(withSuccess(
				"{\"data\":[{\"status\":\"error\",\"message\":\"x\",\"details\":{\"error\":\"DeviceNotRegistered\"}},"
						+ "{\"status\":\"error\",\"message\":\"y\"}]}", MediaType.APPLICATION_JSON));

		// when
		List<PushTicketResult> results = client.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]")));

		// then
		assertThat(results).containsExactly(
				new PushTicketResult(null, "DeviceNotRegistered"), new PushTicketResult(null, "UNKNOWN"));
	}

	@Test
	void 과다_요청과_서버_오류는_일시_오류다() {
		// given, when, then
		for (HttpStatus status : List.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE)) {
			ExpoPushClient client = client("");
			server.expect(requestTo(SEND)).andRespond(withStatus(status).body("ExponentPushToken[leak]"));
			Throwable thrown = catchThrowable(() -> client.send(List.of(message("ExponentPushToken[a]"))));
			assertThat(thrown).isInstanceOf(PushTransientException.class).hasNoCause();
			assertThat(thrown.getMessage()).doesNotContain("leak").doesNotContain("ExponentPushToken");
		}
	}

	@Test
	void 잘못된_요청과_errors_응답은_요청_오류다() {
		// given
		ExpoPushClient bad = client("");
		server.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("ExponentPushToken[leak]"));

		// when
		Throwable badRequest = catchThrowable(() -> bad.send(List.of(message("ExponentPushToken[a]"))));

		// then
		assertThat(badRequest).isInstanceOf(PushRequestException.class).hasNoCause();
		assertThat(badRequest.getMessage()).doesNotContain("leak").doesNotContain("ExponentPushToken");

		// given
		ExpoPushClient errors = client("");
		server.expect(requestTo(SEND)).andRespond(
				withSuccess("{\"errors\":[{\"code\":\"UNAUTHORIZED\",\"message\":\"ExponentPushToken[leak]\"}]}",
						MediaType.APPLICATION_JSON));

		// when
		Throwable errorBody = catchThrowable(() -> errors.send(List.of(message("ExponentPushToken[a]"))));

		// then
		assertThat(errorBody).isInstanceOf(PushRequestException.class).hasNoCause().hasMessage("UNAUTHORIZED");
	}

	@Test
	void 티켓_수가_다르면_요청_오류다() {
		// given
		ExpoPushClient client = client("");
		server.expect(requestTo(SEND)).andRespond(
				withSuccess("{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"}]}", MediaType.APPLICATION_JSON));

		// when
		Throwable thrown = catchThrowable(
				() -> client.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]"))));

		// then
		assertThat(thrown).isInstanceOf(PushRequestException.class);
	}

	@Test
	void 수신_확인을_ID별로_돌려준다() {
		// given
		ExpoPushClient client = client("tok");
		server.expect(requestTo(RECEIPTS))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer tok"))
				.andExpect(jsonPath("$.ids.length()").value(3))
				.andExpect(jsonPath("$.ids[0]").value("t1"))
				.andRespond(withSuccess("{\"data\":{\"t1\":{\"status\":\"ok\"},"
						+ "\"t2\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}},"
						+ "\"t3\":{\"status\":\"error\"}}}", MediaType.APPLICATION_JSON));

		// when
		Map<String, PushReceipt> receipts = client.receipts(List.of("t1", "t2", "t3"));

		// then
		assertThat(receipts).containsOnly(
				Map.entry("t1", new PushReceipt(null)),
				Map.entry("t2", new PushReceipt("DeviceNotRegistered")),
				Map.entry("t3", new PushReceipt("UNKNOWN")));
	}

	private static ExpoPushClient failingClient(String accessToken, RuntimeException failure) {
		RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
			throw failure;
		}).build();
		return new ExpoPushClient(restClient, new PushProperties(true, "https://exp.host", accessToken));
	}

	@Test
	void 잘못된_액세스_토큰으로_생긴_예외는_값을_담지_않는_요청_오류가_된다() {
		// given
		String secret = "bad\nsecret-value";
		IllegalArgumentException failure = new IllegalArgumentException("invalid header value: Bearer " + secret);
		ExpoPushClient client = failingClient(secret, failure);

		// when
		Throwable onSend = catchThrowable(() -> client.send(List.of(message("ExponentPushToken[a]"))));
		Throwable onReceipts = catchThrowable(() -> client.receipts(List.of("t1")));

		// then
		for (Throwable thrown : List.of(onSend, onReceipts)) {
			assertThat(thrown).isInstanceOf(PushRequestException.class).hasNoCause();
			assertThat(thrown.getMessage()).doesNotContain("secret-value").doesNotContain("Bearer");
		}
	}

	@Test
	void 읽기_시간_초과는_다시_보내지_않는_요청_오류다() throws Exception {
		// given: 연결은 받고 응답은 하지 않는 서버
		try (java.net.ServerSocket silent = new java.net.ServerSocket(0, 10, java.net.InetAddress.getLoopbackAddress())) {
			ExpoPushClient client = realClient(silent.getLocalPort());

			// when
			Throwable onSend = catchThrowable(() -> client.send(List.of(message("ExponentPushToken[a]"))));
			Throwable onReceipts = catchThrowable(() -> client.receipts(List.of("t1")));

			// then
			assertThat(onSend).isInstanceOf(PushRequestException.class).hasNoCause();
			assertThat(((PushRequestException) onSend).getCode()).isEqualTo("READ_TIMEOUT");
			assertThat(onReceipts).isNotInstanceOf(PushTransientException.class).isInstanceOf(PushRequestException.class);
		}
	}

	@Test
	void 연결_거부는_일시_오류다() throws Exception {
		// given: 닫힌 포트
		int closedPort;
		try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
			closedPort = probe.getLocalPort();
		}
		ExpoPushClient client = realClient(closedPort);

		// when
		Throwable thrown = catchThrowable(() -> client.send(List.of(message("ExponentPushToken[a]"))));

		// then
		assertThat(thrown).isInstanceOf(PushTransientException.class).hasNoCause();
		assertThat(((PushTransientException) thrown).getCode()).isEqualTo("CONNECTION");
	}

	@Test
	void 연결_뒤의_그_밖의_입출력_실패는_다시_보내지_않는_요청_오류다() {
		// given
		ExpoPushClient client = failingClient("",
				new org.springframework.web.client.ResourceAccessException("I/O error", new java.io.IOException("Connection reset")));

		// when
		Throwable thrown = catchThrowable(() -> client.send(List.of(message("ExponentPushToken[a]"))));

		// then
		assertThat(thrown).isInstanceOf(PushRequestException.class).hasNoCause();
		assertThat(((PushRequestException) thrown).getCode()).isEqualTo("IO_ERROR");
	}

	@Test
	void 알_수_없는_호스트는_일시_오류다() {
		// given
		ExpoPushClient client = failingClient("", new org.springframework.web.client.ResourceAccessException("I/O error",
				new java.net.UnknownHostException("exp.invalid")));

		// when
		Throwable thrown = catchThrowable(() -> client.send(List.of(message("ExponentPushToken[a]"))));

		// then
		assertThat(thrown).isInstanceOf(PushTransientException.class).hasNoCause();
	}

	/** PushConfig와 같은 방식(detect)으로 만든 실제 HTTP 클라이언트, 짧은 시간 제한, 로컬 포트만. */
	private static ExpoPushClient realClient(int port) {
		org.springframework.boot.http.client.HttpClientSettings settings = org.springframework.boot.http.client.HttpClientSettings
				.defaults().withTimeouts(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(300));
		RestClient restClient = RestClient.builder()
				.requestFactory(org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
		return new ExpoPushClient(restClient, new PushProperties(true, "http://127.0.0.1:" + port, ""));
	}

	@Test
	void 형식이_다른_오류_값은_UNKNOWN으로_바꾼다() {
		// given
		ExpoPushClient client = client("");
		server.expect(requestTo(SEND)).andRespond(withSuccess(
				"{\"data\":[{\"status\":\"error\",\"details\":{\"error\":\"bad ExponentPushToken[leak]\"}}]}",
				MediaType.APPLICATION_JSON));

		// when
		List<PushTicketResult> results = client.send(List.of(message("ExponentPushToken[a]")));

		// then
		assertThat(results).containsExactly(new PushTicketResult(null, "UNKNOWN"));
	}

	@Test
	void 수신_확인의_형식이_다른_오류_값도_UNKNOWN이다() {
		// given
		ExpoPushClient client = client("");
		server.expect(requestTo(RECEIPTS)).andRespond(withSuccess(
				"{\"data\":{\"t1\":{\"status\":\"error\",\"details\":{\"error\":\"ExponentPushToken[leak]\"}},"
						+ "\"t2\":{\"status\":\"error\",\"details\":{\"error\":\"MessageRateExceeded\"}}}}",
				MediaType.APPLICATION_JSON));

		// when
		Map<String, PushReceipt> receipts = client.receipts(List.of("t1", "t2"));

		// then
		assertThat(receipts).containsOnly(
				Map.entry("t1", new PushReceipt("UNKNOWN")),
				Map.entry("t2", new PushReceipt("MessageRateExceeded")));
	}
}
