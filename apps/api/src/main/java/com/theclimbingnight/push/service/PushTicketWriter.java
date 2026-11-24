package com.theclimbingnight.push.service;

import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.repository.PushTicketRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 티켓 저장과 토큰·티켓 삭제. 호출한 쪽이 이미 커밋된 트랜잭션 안(커밋 후 단계)이어도 저장되게 항상 새 트랜잭션으로 한다.
 */
@Component
public class PushTicketWriter {

	private final PushTicketRepository ticketRepository;
	private final PushTokenRepository tokenRepository;

	public PushTicketWriter(PushTicketRepository ticketRepository, PushTokenRepository tokenRepository) {
		this.ticketRepository = ticketRepository;
		this.tokenRepository = tokenRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveAll(List<PushTicket> tickets) {
		ticketRepository.saveAll(tickets);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteTickets(Collection<Long> ids) {
		ticketRepository.deleteAllByIdInBatch(ids);
	}

	/** 토큰마다 그 토큰에 마지막으로 발송한 시각 이후 다시 등록된 것은 남긴다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteTokens(Map<String, Instant> sentAtByToken) {
		sentAtByToken.forEach(tokenRepository::deleteIfNotSeenSince);
	}
}
