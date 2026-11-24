package com.theclimbingnight.push.service;

import com.theclimbingnight.common.FailureSummary;
import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.expo.PushGateway;
import com.theclimbingnight.push.expo.PushReceipt;
import com.theclimbingnight.push.repository.PushTicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 15분 지난 티켓의 수신 확인을 조회해 무효 기기의 토큰을 지운다. 트랜잭션이 없고, 삭제는 {@link PushTicketWriter}가 한다.
 * 결과가 없는 티켓은 남겨 두므로 id를 기준으로 이어 읽어야 같은 티켓을 되풀이하지 않는다.
 */
@Component
public class PushReceiptJob {

	private static final Logger log = LoggerFactory.getLogger(PushReceiptJob.class);
	private static final int PAGE_SIZE = 1000;
	private static final Duration MIN_AGE = Duration.ofMinutes(15);
	private static final Duration MAX_AGE = Duration.ofHours(24);
	private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

	private final PushTicketRepository repository;
	private final PushGateway gateway;
	private final PushTicketWriter writer;
	private final Clock clock;

	public PushReceiptJob(PushTicketRepository repository, PushGateway gateway, PushTicketWriter writer, Clock clock) {
		this.repository = repository;
		this.gateway = gateway;
		this.writer = writer;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 900_000)
	public void run() {
		Instant now = clock.instant();
		Instant cutoff = now.minus(MIN_AGE);
		Instant expiry = now.minus(MAX_AGE);
		long lastId = 0;
		while (true) {
			List<PushTicket> page;
			try {
				page = repository.findDue(cutoff, lastId, PageRequest.of(0, PAGE_SIZE));
			} catch (RuntimeException e) {
				log.error("푸시 수신 확인 대상 조회 실패 type={}", FailureSummary.of(e));
				return;
			}
			if (page.isEmpty()) {
				return;
			}
			lastId = page.get(page.size() - 1).getId();
			try {
				process(page, expiry);
			} catch (RuntimeException e) {
				log.error("푸시 수신 확인 처리 실패 type={}", FailureSummary.of(e));
			}
			if (page.size() < PAGE_SIZE) {
				return;
			}
		}
	}

	private void process(List<PushTicket> page, Instant expiry) {
		Map<String, PushReceipt> receipts = Map.of();
		try {
			receipts = gateway.receipts(page.stream().map(PushTicket::getTicketId).toList());
		} catch (RuntimeException e) {
			// 토큰이 담길 수 있어 예외 객체는 로그에 넘기지 않는다. 이 쪽의 오래된 티켓 정리는 아래에서 계속한다.
			log.error("푸시 수신 확인 조회 실패 type={}", FailureSummary.of(e));
		}
		List<Long> done = new ArrayList<>();
		Map<String, Instant> invalid = new LinkedHashMap<>();
		Map<String, List<Long>> invalidTicketIdsByToken = new HashMap<>();
		for (PushTicket ticket : page) {
			PushReceipt receipt = receipts.get(ticket.getTicketId());
			if (receipt == null) {
				if (ticket.getSentAt().isBefore(expiry)) {
					done.add(ticket.getId());
				}
				continue;
			}
			done.add(ticket.getId());
			if (receipt.error() == null) {
				continue;
			}
			if (DEVICE_NOT_REGISTERED.equals(receipt.error())) {
				// 같은 토큰에 여러 티켓이면 가장 늦은 발송 시각을 쓴다. 티켓마다 지우는 것과 같은 결과다.
				invalidTicketIdsByToken.computeIfAbsent(ticket.getExpoPushToken(), k -> new ArrayList<>()).add(ticket.getId());
				invalid.merge(ticket.getExpoPushToken(), ticket.getSentAt(), (a, b) -> a.isAfter(b) ? a : b);
			} else {
				log.error("푸시 수신 오류 code={}", receipt.error());
			}
		}
		if (!invalid.isEmpty()) {
			Set<String> failedTokens;
			try {
				failedTokens = writer.deleteTokens(invalid);
			} catch (RuntimeException e) {
				log.error("무효 푸시 토큰 삭제 실패 type={}", FailureSummary.of(e));
				failedTokens = invalid.keySet();
			}
			Set<String> failed = failedTokens;
			if (!failed.isEmpty()) {
				// 토큰이 남았으니 그 토큰의 티켓도 남겨 다음 실행에서 다시 시도한다.
				done.removeAll(invalidTicketIdsByToken.entrySet().stream()
						.filter(entry -> failed.contains(entry.getKey()))
						.flatMap(entry -> entry.getValue().stream()).toList());
			}
		}
		if (!done.isEmpty()) {
			try {
				writer.deleteTickets(done);
			} catch (RuntimeException e) {
				log.error("푸시 티켓 삭제 실패 type={}", FailureSummary.of(e));
			}
		}
	}
}
