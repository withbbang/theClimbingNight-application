package com.theclimbingnight.push.service;

import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.expo.PushGateway;
import com.theclimbingnight.push.expo.PushMessage;
import com.theclimbingnight.push.expo.PushRequestException;
import com.theclimbingnight.push.expo.PushTicketResult;
import com.theclimbingnight.push.expo.PushTransientException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 푸시를 100개씩 나눠 보낸다. 트랜잭션이 없다. 저장·삭제는 {@link PushTicketWriter}가 새 트랜잭션으로 한다.
 * 푸시 토큰은 로그에 남기지 않는다. Hibernate의 SQL 오류 로그도 꺼 두었으므로 DB 실패는 여기서 클래스 이름만 남긴다.
 */
@Component
public class PushSender {

	private static final Logger log = LoggerFactory.getLogger(PushSender.class);
	private static final int BATCH_SIZE = 100;
	private static final int MAX_ATTEMPTS = 3;
	private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

	private final PushGateway gateway;
	private final PushTicketWriter writer;
	private final Pause pause;
	private final Clock clock;

	public PushSender(PushGateway gateway, PushTicketWriter writer, Pause pause, Clock clock) {
		this.gateway = gateway;
		this.writer = writer;
		this.pause = pause;
		this.clock = clock;
	}

	public void send(Collection<String> tokens, String title, String body, Map<String, Object> data) {
		Set<String> unique = new LinkedHashSet<>();
		for (String token : tokens) {
			if (token != null && !token.isBlank()) {
				unique.add(token);
			}
		}
		List<String> all = new ArrayList<>(unique);
		for (int from = 0; from < all.size(); from += BATCH_SIZE) {
			List<String> batch = all.subList(from, Math.min(from + BATCH_SIZE, all.size()));
			try {
				sendBatch(batch, title, body, data);
			} catch (RuntimeException e) {
				// 한 묶음의 어떤 실패도 다음 묶음을 막지 않는다. 예외 객체는 넘기지 않는다 (메시지에 토큰이 있을 수 있음).
				log.error("푸시 묶음 처리 실패 type={}", e.getClass().getSimpleName());
			}
		}
	}

	private void sendBatch(List<String> batch, String title, String body, Map<String, Object> data) {
		List<PushMessage> messages = batch.stream().map(token -> new PushMessage(token, title, body, data)).toList();
		Instant sentAt = clock.instant();
		List<PushTicketResult> results;
		try {
			results = sendWithRetry(messages);
		} catch (PushTransientException e) {
			log.error("푸시 발송 실패(재시도 소진) code={} count={}", e.getCode(), batch.size());
			return;
		} catch (PushRequestException e) {
			log.error("푸시 발송 실패 code={} count={}", e.getCode(), batch.size());
			return;
		}
		if (results.isEmpty()) {
			return;
		}
		List<PushTicket> tickets = new ArrayList<>();
		Map<String, Instant> invalid = new LinkedHashMap<>();
		for (int i = 0; i < Math.min(results.size(), batch.size()); i++) {
			PushTicketResult result = results.get(i);
			if (result.error() == null) {
				tickets.add(PushTicket.of(result.ticketId(), batch.get(i), sentAt));
			} else if (DEVICE_NOT_REGISTERED.equals(result.error())) {
				invalid.put(batch.get(i), sentAt);
			} else {
				log.warn("푸시 티켓 오류 code={}", result.error());
			}
		}
		if (!tickets.isEmpty()) {
			try {
				writer.saveAll(tickets);
			} catch (RuntimeException e) {
				log.error("푸시 티켓 저장 실패 type={}", e.getClass().getSimpleName());
			}
		}
		if (!invalid.isEmpty()) {
			try {
				writer.deleteTokens(invalid);
			} catch (RuntimeException e) {
				log.error("무효 푸시 토큰 삭제 실패 type={}", e.getClass().getSimpleName());
			}
		}
	}

	/** 일시 오류면 2초, 4초 뒤 다시 보낸다 (총 3번). */
	private List<PushTicketResult> sendWithRetry(List<PushMessage> messages) {
		Duration wait = Duration.ofSeconds(2);
		for (int attempt = 1;; attempt++) {
			try {
				return gateway.send(messages);
			} catch (PushTransientException e) {
				if (attempt >= MAX_ATTEMPTS || Thread.currentThread().isInterrupted()) {
					throw e;
				}
				pause.sleep(wait);
				wait = wait.multipliedBy(2);
			}
		}
	}
}
