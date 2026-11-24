package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.push.repository.StalePushToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PushTokenCleanupJobTest {

	private static final Instant NOW = Instant.parse("2026-11-24T19:00:00Z");

	private static final Instant SEEN = NOW.minus(Duration.ofDays(61));

	@Mock PushTokenRepository repository;
	@Mock PushTokenDeleter deleter;
	PushTokenCleanupJob job;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties(null, null, Duration.ofDays(30), null, null, null, null, null);
		job = new PushTokenCleanupJob(repository, deleter, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void 정리_대상을_조회한_등록_시각_기준으로_지운다() {
		// given
		given(repository.findStaleTokens(NOW, NOW.minus(Duration.ofDays(60)))).willReturn(List.of(new StalePushToken("A", SEEN), new StalePushToken("B", SEEN.plusSeconds(1))));

		// when
		job.run();

		// then: 조회와 겹쳐 다시 등록된 토큰은 지우지 않게 조회한 등록 시각을 넘긴다
		verify(deleter).deleteIfNotSeenSince("A", SEEN);
		verify(deleter).deleteIfNotSeenSince("B", SEEN.plusSeconds(1));
	}

	@Test
	void 하나가_실패해도_나머지를_지우고_토큰은_로그에_남기지_않는다(CapturedOutput output) {
		// given
		given(repository.findStaleTokens(any(), any())).willReturn(List.of(new StalePushToken("secretA", SEEN), new StalePushToken("B", SEEN)));
		willThrow(new IllegalStateException("secretA")).given(deleter).deleteIfNotSeenSince("secretA", SEEN);

		// when
		job.run();

		// then
		verify(deleter).deleteIfNotSeenSince("B", SEEN);
		assertThat(output.getAll()).doesNotContain("secretA");
	}

	@Test
	void 조회가_실패하면_예외_없이_끝낸다() {
		// given
		given(repository.findStaleTokens(any(), any())).willThrow(new IllegalStateException("x"));

		// when, then
		assertThatCode(() -> job.run()).doesNotThrowAnyException();
		verifyNoInteractions(deleter);
	}
}
