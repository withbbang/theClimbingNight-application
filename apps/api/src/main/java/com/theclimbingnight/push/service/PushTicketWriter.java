package com.theclimbingnight.push.service;

import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.repository.PushTicketRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 티켓 저장과 토큰·티켓 삭제. 호출한 쪽이 이미 커밋된 트랜잭션 안(커밋 후 단계)이어도 저장되게 항상 새 트랜잭션으로 한다.
 */
@Component
public class PushTicketWriter {

	private static final Logger log = LoggerFactory.getLogger(PushTicketWriter.class);

	private final PushTicketRepository ticketRepository;
	private final PushTokenDeleter tokenDeleter;

	public PushTicketWriter(PushTicketRepository ticketRepository, PushTokenDeleter tokenDeleter) {
		this.ticketRepository = ticketRepository;
		this.tokenDeleter = tokenDeleter;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveAll(List<PushTicket> tickets) {
		ticketRepository.saveAll(tickets);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteTickets(Collection<Long> ids) {
		ticketRepository.deleteAllByIdInBatch(ids);
	}

	/**
	 * 토큰마다 자기 트랜잭션(REQUIRES_NEW)으로 지워 행 잠금을 오래 쥐지 않는다. 마지막 발송 이후 다시 등록된 토큰은 남긴다.
	 * 실패한 토큰은 돌려주고 나머지는 계속 지운다.
	 */
	public Set<String> deleteTokens(Map<String, Instant> sentAtByToken) {
		Set<String> failed = new LinkedHashSet<>();
		sentAtByToken.forEach((token, sentAt) -> {
			try {
				tokenDeleter.deleteIfNotSeenSince(token, sentAt);
			} catch (RuntimeException e) {
				// 예외 메시지에 토큰이 있을 수 있어 로그에 넘기지 않고 호출한 쪽이 클래스 이름만 남긴다.
				failed.add(token);
				log.error("무효 푸시 토큰 삭제 실패 type={}", e.getClass().getSimpleName());
			}
		});
		return failed;
	}
}
