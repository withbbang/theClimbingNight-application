package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.expo.PushGateway;
import com.theclimbingnight.push.expo.PushReceipt;
import com.theclimbingnight.push.expo.PushRequestException;
import com.theclimbingnight.push.repository.PushTicketRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PushReceiptJobTest {

	private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

	@Mock PushTicketRepository repository;
	@Mock PushGateway gateway;
	@Mock PushTicketWriter writer;
	@Captor ArgumentCaptor<List<String>> ids;
	@Captor ArgumentCaptor<Collection<Long>> deletedIds;
	@Captor ArgumentCaptor<Instant> cutoff;
	@Captor ArgumentCaptor<Long> lastId;
	PushReceiptJob job;

	@BeforeEach
	void setUp() {
		job = new PushReceiptJob(repository, gateway, writer, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static PushTicket ticket(long id, Instant sentAt) {
		return ticket(id, "ExponentPushToken[" + id + "]", sentAt);
	}

	private static PushTicket ticket(long id, String token, Instant sentAt) {
		PushTicket ticket = PushTicket.of("t" + id, token, sentAt);
		try {
			Field field = PushTicket.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(ticket, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return ticket;
	}

	@Test
	void 십오_분이_지난_티켓만_확인한다() {
		// given
		given(repository.findDue(any(), anyLong(), any(Pageable.class))).willReturn(List.of());

		// when
		job.run();

		// then
		verify(repository).findDue(cutoff.capture(), lastId.capture(), eq(org.springframework.data.domain.PageRequest.of(0, 1000)));
		assertThat(cutoff.getValue()).isEqualTo(NOW.minus(Duration.ofMinutes(15)));
		assertThat(lastId.getValue()).isZero();
		verify(gateway, never()).receipts(anyList());
	}

	@Test
	void 무효_기기면_토큰과_티켓을_지운다() {
		// given
		Instant sentAt = NOW.minus(Duration.ofMinutes(20));
		given(repository.findDue(any(), anyLong(), any(Pageable.class))).willReturn(List.of(ticket(1, sentAt), ticket(2, sentAt)));
		given(gateway.receipts(anyList())).willReturn(Map.of(
				"t1", new PushReceipt("DeviceNotRegistered"), "t2", new PushReceipt(null)));

		// when
		job.run();

		// then
		verify(writer).deleteTokens(Map.of("ExponentPushToken[1]", sentAt));
		verify(writer).deleteTickets(deletedIds.capture());
		assertThat(deletedIds.getValue()).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void 결과가_없는_티켓은_남기고_하루가_지나면_지운다() {
		// given
		PushTicket recent = ticket(1, NOW.minus(Duration.ofHours(23)));
		PushTicket old = ticket(2, NOW.minus(Duration.ofHours(25)));
		given(repository.findDue(any(), anyLong(), any(Pageable.class))).willReturn(List.of(recent, old));
		given(gateway.receipts(anyList())).willReturn(Map.of());

		// when
		job.run();

		// then
		verify(writer).deleteTickets(deletedIds.capture());
		assertThat(deletedIds.getValue()).containsExactly(2L);
		verify(writer, never()).deleteTokens(any());
	}

	@Test
	void 천_개가_넘으면_나눠_조회한다() {
		// given
		Instant sentAt = NOW.minus(Duration.ofMinutes(20));
		List<PushTicket> first = IntStream.rangeClosed(1, 1000).mapToObj(i -> ticket(i, sentAt)).toList();
		List<PushTicket> second = List.of(ticket(1001, sentAt));
		given(repository.findDue(any(), eq(0L), any(Pageable.class))).willReturn(first);
		given(repository.findDue(any(), eq(1000L), any(Pageable.class))).willReturn(second);
		given(gateway.receipts(anyList())).willReturn(Map.of());

		// when
		job.run();

		// then
		verify(gateway, times(2)).receipts(ids.capture());
		assertThat(ids.getAllValues().get(0)).hasSize(1000);
		assertThat(ids.getAllValues().get(1)).containsExactly("t1001");
		verify(repository, times(2)).findDue(any(), anyLong(), any(Pageable.class));
	}

	@Test
	void 게이트웨이가_실패해도_다음_쪽을_읽고_예외를_밖으로_던지지_않는다() {
		// given
		Instant sentAt = NOW.minus(Duration.ofMinutes(20));
		List<PushTicket> first = new ArrayList<>(IntStream.rangeClosed(1, 1000).mapToObj(i -> ticket(i, sentAt)).toList());
		given(repository.findDue(any(), eq(0L), any(Pageable.class))).willReturn(first);
		given(repository.findDue(any(), eq(1000L), any(Pageable.class))).willReturn(List.of());
		given(gateway.receipts(anyList())).willThrow(new PushRequestException("HTTP_400"));

		// when
		job.run();

		// then
		verify(repository, times(2)).findDue(any(), anyLong(), any(Pageable.class));
		verify(writer, never()).deleteTokens(any());
	}

	@Test
	void 삭제가_실패해도_다음_쪽을_계속한다() {
		// given
		Instant sentAt = NOW.minus(Duration.ofMinutes(20));
		List<PushTicket> first = IntStream.rangeClosed(1, 1000).mapToObj(i -> ticket(i, sentAt)).toList();
		given(repository.findDue(any(), eq(0L), any(Pageable.class))).willReturn(first);
		given(repository.findDue(any(), eq(1000L), any(Pageable.class))).willReturn(List.of(ticket(1001, sentAt)));
		given(gateway.receipts(anyList())).willReturn(Map.of("t1", new PushReceipt(null)), Map.of("t1001", new PushReceipt(null)));
		org.mockito.BDDMockito.willThrow(new IllegalStateException("x")).willDoNothing().given(writer).deleteTickets(any());

		// when
		job.run();

		// then
		verify(writer, times(2)).deleteTickets(any());
	}

	@Test
	void 같은_토큰에_무효_티켓이_여럿이면_가장_늦은_발송_시각으로_지운다() {
		// given
		Instant first = NOW.minus(Duration.ofMinutes(50));
		Instant second = NOW.minus(Duration.ofMinutes(40));
		PushTicket a = ticket(1, "ExponentPushToken[same]", first);
		PushTicket b = ticket(2, "ExponentPushToken[same]", second);
		given(repository.findDue(any(), anyLong(), any(Pageable.class))).willReturn(List.of(a, b));
		given(gateway.receipts(anyList())).willReturn(Map.of(
				"t1", new PushReceipt("DeviceNotRegistered"), "t2", new PushReceipt("DeviceNotRegistered")));

		// when
		job.run();

		// then
		verify(writer).deleteTokens(Map.of("ExponentPushToken[same]", second));
	}

	@Test
	void 토큰_삭제에_실패한_티켓은_남기고_나머지는_지운다() {
		// given
		Instant sentAt = NOW.minus(Duration.ofMinutes(20));
		given(repository.findDue(any(), anyLong(), any(Pageable.class)))
				.willReturn(List.of(ticket(1, sentAt), ticket(2, sentAt), ticket(3, sentAt)));
		given(gateway.receipts(anyList())).willReturn(Map.of(
				"t1", new PushReceipt("DeviceNotRegistered"),
				"t2", new PushReceipt("DeviceNotRegistered"),
				"t3", new PushReceipt(null)));
		given(writer.deleteTokens(any())).willReturn(java.util.Set.of("ExponentPushToken[1]"));

		// when
		job.run();

		// then
		verify(writer).deleteTickets(deletedIds.capture());
		assertThat(deletedIds.getValue()).containsExactlyInAnyOrder(2L, 3L);
	}
}
