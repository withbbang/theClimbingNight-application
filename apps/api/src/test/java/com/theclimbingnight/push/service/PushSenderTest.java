package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.expo.PushGateway;
import com.theclimbingnight.push.expo.PushMessage;
import com.theclimbingnight.push.expo.PushRequestException;
import com.theclimbingnight.push.expo.PushTicketResult;
import com.theclimbingnight.push.expo.PushTransientException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSenderTest {

	private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

	@Mock PushGateway gateway;
	@Mock PushTicketWriter writer;
	@Mock Pause pause;
	@Captor ArgumentCaptor<List<PushMessage>> messages;
	@Captor ArgumentCaptor<List<PushTicket>> tickets;
	PushSender sender;

	@BeforeEach
	void setUp() {
		sender = new PushSender(gateway, writer, pause, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static List<String> tokens(int count) {
		return IntStream.range(0, count).mapToObj(i -> "ExponentPushToken[" + i + "]").toList();
	}

	private static List<PushTicketResult> oks(int count) {
		return IntStream.range(0, count).mapToObj(i -> new PushTicketResult("t" + i, null)).toList();
	}

	@Test
	void 백_개씩_나눠_보낸다() {
		// given
		given(gateway.send(anyList())).willAnswer(i -> oks(((List<?>) i.getArgument(0)).size()));

		// when
		sender.send(tokens(100), "제목", "본문", Map.of());
		// then
		verify(gateway, times(1)).send(anyList());

		// when
		sender.send(tokens(101), "제목", "본문", Map.of());
		// then
		verify(gateway, times(3)).send(messages.capture());
		assertThat(messages.getAllValues().get(1)).hasSize(100);
		assertThat(messages.getAllValues().get(2)).hasSize(1);
	}

	@Test
	void 토큰이_없으면_보내지_않고_중복은_합친다() {
		// given
		given(gateway.send(anyList())).willAnswer(i -> oks(((List<?>) i.getArgument(0)).size()));

		// when
		sender.send(List.of(), "제목", "본문", Map.of());
		// then
		verify(gateway, never()).send(anyList());

		// when
		sender.send(List.of("ExponentPushToken[a]", "ExponentPushToken[a]"), "제목", "본문", Map.of());
		// then
		verify(gateway).send(messages.capture());
		assertThat(messages.getValue()).hasSize(1);
	}

	@Test
	void 무효_토큰은_지우고_나머지는_보낸다() {
		// given
		given(gateway.send(anyList())).willReturn(List.of(
				new PushTicketResult("t0", null),
				new PushTicketResult(null, "DeviceNotRegistered"),
				new PushTicketResult(null, "MessageRateExceeded")));

		// when
		sender.send(tokens(3), "제목", "본문", Map.of());

		// then
		verify(writer).saveAll(tickets.capture());
		assertThat(tickets.getValue()).hasSize(1);
		assertThat(tickets.getValue().get(0).getTicketId()).isEqualTo("t0");
		assertThat(tickets.getValue().get(0).getExpoPushToken()).isEqualTo("ExponentPushToken[0]");
		assertThat(tickets.getValue().get(0).getSentAt()).isEqualTo(NOW);
		verify(writer).deleteTokens(Map.of("ExponentPushToken[1]", NOW));
	}

	@Test
	void 일시_오류는_두_번까지_다시_보낸다() {
		// given
		given(gateway.send(anyList()))
				.willThrow(new PushTransientException("HTTP_503"))
				.willThrow(new PushTransientException("HTTP_503"))
				.willReturn(oks(1));

		// when
		sender.send(tokens(1), "제목", "본문", Map.of());

		// then
		verify(gateway, times(3)).send(anyList());
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(pause);
		order.verify(pause).sleep(Duration.ofSeconds(2));
		order.verify(pause).sleep(Duration.ofSeconds(4));
		verify(pause, times(2)).sleep(any());
		verify(writer).saveAll(anyList());
	}

	@Test
	void 일시_오류가_세_번_이어지면_포기한다() {
		// given
		given(gateway.send(anyList())).willThrow(new PushTransientException("HTTP_503"));

		// when
		sender.send(tokens(1), "제목", "본문", Map.of());

		// then
		verify(gateway, times(3)).send(anyList());
		verify(writer, never()).saveAll(anyList());
	}

	@Test
	void 요청_오류는_다시_보내지_않는다() {
		// given
		given(gateway.send(anyList())).willThrow(new PushRequestException("HTTP_400"));

		// when
		sender.send(tokens(1), "제목", "본문", Map.of());

		// then
		verify(gateway, times(1)).send(anyList());
		verify(pause, never()).sleep(any());
		verify(writer, never()).saveAll(anyList());
	}

	@Test
	void 한_묶음이_실패해도_다음_묶음을_보낸다() {
		// given
		given(gateway.send(anyList()))
				.willThrow(new PushRequestException("HTTP_400"))
				.willReturn(oks(1));

		// when
		sender.send(tokens(101), "제목", "본문", Map.of());

		// then
		verify(gateway, times(2)).send(anyList());
		verify(writer, times(1)).saveAll(anyList());
	}

	@Test
	void 저장이_실패해도_다음_묶음을_보낸다() {
		// given
		given(gateway.send(anyList())).willAnswer(i -> oks(((List<?>) i.getArgument(0)).size()));
		willThrow(new IllegalStateException("could not execute ExponentPushToken[0]"))
				.willDoNothing().given(writer).saveAll(anyList());

		// when
		sender.send(tokens(101), "제목", "본문", Map.of());

		// then
		verify(gateway, times(2)).send(anyList());
		verify(writer, times(2)).saveAll(anyList());
	}

	@Test
	void 성공한_티켓만_저장한다() {
		// given
		List<PushTicketResult> results = new ArrayList<>(oks(2));
		results.add(new PushTicketResult(null, "UNKNOWN"));
		given(gateway.send(anyList())).willReturn(results);

		// when
		sender.send(tokens(3), "제목", "본문", Map.of());

		// then
		verify(writer).saveAll(tickets.capture());
		assertThat(tickets.getValue()).extracting(PushTicket::getTicketId).containsExactly("t0", "t1");
		verify(writer, never()).deleteTokens(any());
	}

	@Test
	void 빈_결과면_티켓을_저장하지_않는다() {
		// given
		given(gateway.send(anyList())).willReturn(List.of());

		// when
		sender.send(tokens(2), "제목", "본문", Map.of());

		// then
		verify(writer, never()).saveAll(anyList());
		verify(writer, never()).deleteTokens(any());
	}
}
