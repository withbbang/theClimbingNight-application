package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

	private final RetryPolicy policy = new RetryPolicy();

	@Test
	void 첫_실패는_1분_뒤에_다시_한다() {
		// when / then
		assertThat(policy.nextRunAt(1, NOW)).contains(NOW.plus(Duration.ofMinutes(1)));
	}

	@Test
	void 두_번째_실패는_5분_뒤에_다시_한다() {
		// when / then
		assertThat(policy.nextRunAt(2, NOW)).contains(NOW.plus(Duration.ofMinutes(5)));
	}

	@Test
	void 세_번째_실패부터는_다시_하지_않는다() {
		// when / then
		assertThat(policy.nextRunAt(3, NOW)).isEmpty();
		assertThat(policy.nextRunAt(4, NOW)).isEmpty();
	}
}
