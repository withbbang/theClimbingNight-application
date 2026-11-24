package com.theclimbingnight.push.service;

import com.theclimbingnight.push.repository.PushTokenRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 토큰 하나를 자기 트랜잭션으로 지운다. 별도 빈이라 {@link PushTicketWriter}가 프록시를 거쳐 부른다. */
@Component
public class PushTokenDeleter {

	private final PushTokenRepository tokenRepository;

	public PushTokenDeleter(PushTokenRepository tokenRepository) {
		this.tokenRepository = tokenRepository;
	}

	/** sentAt 이후에 다시 등록된 토큰은 남긴다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteIfNotSeenSince(String token, Instant sentAt) {
		tokenRepository.deleteIfNotSeenSince(token, sentAt);
	}
}
