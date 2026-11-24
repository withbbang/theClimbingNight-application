package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.push.repository.PushTicketRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushTicketWriterDeleteTest {

	private static final Instant SENT = Instant.parse("2026-11-24T10:00:00Z");

	@Mock PushTicketRepository ticketRepository;
	@Mock PushTokenDeleter deleter;
	@InjectMocks PushTicketWriter writer;

	@Test
	void 토큰_하나가_실패해도_나머지는_지우고_실패한_토큰을_돌려준다() {
		// given
		Map<String, Instant> targets = new LinkedHashMap<>();
		targets.put("a", SENT);
		targets.put("b", SENT);
		targets.put("c", SENT);
		org.mockito.Mockito.lenient().doThrow(new IllegalStateException("ExponentPushToken[b]")).when(deleter).deleteIfNotSeenSince("b", SENT);

		// when
		Set<String> failed = writer.deleteTokens(targets);

		// then
		assertThat(failed).containsExactly("b");
		verify(deleter).deleteIfNotSeenSince("a", SENT);
		verify(deleter).deleteIfNotSeenSince("c", SENT);
	}
}
