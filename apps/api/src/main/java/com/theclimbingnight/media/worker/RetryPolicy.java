package com.theclimbingnight.media.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 실패하면 1분, 5분 뒤에 다시 하고, 세 번째 실패에서 멈춘다. */
public class RetryPolicy {

	static final int MAX_ATTEMPTS = 3;
	private static final Duration[] DELAYS = {Duration.ofMinutes(1), Duration.ofMinutes(5)};

	/** attemptsAfterFailure: 이번 실패까지 센 시도 횟수. 더 하지 않으면 empty. */
	public Optional<Instant> nextRunAt(int attemptsAfterFailure, Instant now) {
		if (attemptsAfterFailure < 1 || attemptsAfterFailure >= MAX_ATTEMPTS) {
			return Optional.empty();
		}
		return Optional.of(now.plus(DELAYS[attemptsAfterFailure - 1]));
	}

	/** 실행 전에 이미 시도 횟수를 다 썼는지. 처리 중 워커가 죽어 RUNNING에서 되돌려진 횟수도 센다. */
	public boolean exhausted(int attempts) {
		return attempts >= MAX_ATTEMPTS;
	}
}
