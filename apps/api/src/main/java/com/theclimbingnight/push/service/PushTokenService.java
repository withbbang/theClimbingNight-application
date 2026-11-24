package com.theclimbingnight.push.service;

import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.repository.PushTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushTokenService {

	private static final String CONSTRAINT_TOKEN = "uk_push_tokens_token";

	private final PushTokenWriter writer;
	private final PushTokenRepository pushTokenRepository;

	public PushTokenService(PushTokenWriter writer, PushTokenRepository pushTokenRepository) {
		this.writer = writer;
		this.pushTokenRepository = pushTokenRepository;
	}

	/**
	 * 트랜잭션 없이 호출한다. 같은 토큰의 동시 등록이 유니크 제약에 걸리면 실패한 트랜잭션을 버리고,
	 * 새 트랜잭션에서 한 번만 다시 시도한다 (두 번째에는 먼저 들어간 행을 찾아 소유자만 바꾼다).
	 */
	public void register(long userId, String token, PushPlatform platform) {
		try {
			writer.upsert(userId, token, platform);
		} catch (DataIntegrityViolationException e) {
			if (!ConstraintViolations.violated(e).contains(CONSTRAINT_TOKEN)) {
				throw e;
			}
			try {
				writer.upsert(userId, token, platform);
			} catch (DataIntegrityViolationException second) {
				if (ConstraintViolations.violated(second).contains(CONSTRAINT_TOKEN)) {
					// 원인 예외·메시지에는 토큰 원문이 들어 있어 로그로 새므로 붙이지 않는다.
					throw new IllegalStateException("푸시 토큰 등록이 동시 요청과 계속 충돌했습니다.");
				}
				throw second;
			}
		}
	}

	/** 소유자가 맞을 때만 지운다. 다른 사용자가 이어받은 토큰은 건드리지 않는다. */
	@Transactional
	public void deleteOwned(long userId, String token) {
		pushTokenRepository.deleteByUserIdAndToken(userId, token);
	}
}
