package com.theclimbingnight.push.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 트랜잭션 하나로 토큰을 등록한다. 유니크 위반 재시도는 트랜잭션 밖의 PushTokenService가 맡는다. */
@Component
public class PushTokenWriter {

	private final UserRepository userRepository;
	private final PushTokenRepository pushTokenRepository;
	private final Clock clock;

	public PushTokenWriter(UserRepository userRepository, PushTokenRepository pushTokenRepository, Clock clock) {
		this.userRepository = userRepository;
		this.pushTokenRepository = pushTokenRepository;
		this.clock = clock;
	}

	@Transactional
	public void upsert(long userId, String token, PushPlatform platform) {
		// 잠금 순서: 사용자 행 먼저, 푸시 토큰 행은 마지막.
		userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		Instant now = clock.instant();
		pushTokenRepository.findByExpoPushToken(token).ifPresentOrElse(
				existing -> existing.reassign(userId, platform, now),
				() -> pushTokenRepository.saveAndFlush(PushToken.register(userId, token, platform, now)));
	}
}
