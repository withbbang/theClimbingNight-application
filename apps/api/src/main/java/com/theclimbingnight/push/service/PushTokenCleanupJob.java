package com.theclimbingnight.push.service;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.common.FailureSummary;
import com.theclimbingnight.push.repository.PushTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 세션이 끝난 기기의 푸시 토큰을 하루 한 번 지운다. 로그아웃 없이 세션이 만료되면 토큰이 남아 알림이 계속 가기 때문이다.
 * 같은 사용자의 다른 기기 세션이 살아 있으면 어느 토큰의 세션인지 알 수 없으므로, 리프레시 유효 기간의 두 배 동안
 * 다시 등록되지 않은 토큰만 지운다 (앱은 켜거나 로그인할 때마다 다시 등록한다).
 */
@Component
public class PushTokenCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(PushTokenCleanupJob.class);
	private static final int STALE_TTL_MULTIPLIER = 2;

	private final PushTokenRepository repository;
	private final PushTokenDeleter deleter;
	private final AuthProperties authProperties;
	private final Clock clock;

	public PushTokenCleanupJob(PushTokenRepository repository, PushTokenDeleter deleter, AuthProperties authProperties,
			Clock clock) {
		this.repository = repository;
		this.deleter = deleter;
		this.authProperties = authProperties;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void run() {
		Instant now = clock.instant();
		List<String> tokens;
		try {
			tokens = repository.findStaleTokens(now,
					now.minus(authProperties.refreshTtl().multipliedBy(STALE_TTL_MULTIPLIER)));
		} catch (RuntimeException e) {
			log.error("정리할 푸시 토큰 조회 실패 type={}", FailureSummary.of(e));
			return;
		}
		for (String token : tokens) {
			try {
				// 조회 뒤 다시 로그인해 등록한 토큰은 남긴다.
				deleter.deleteIfNotSeenSince(token, now);
			} catch (RuntimeException e) {
				log.error("만료된 푸시 토큰 삭제 실패 type={}", FailureSummary.of(e));
			}
		}
	}
}
