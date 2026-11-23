package com.theclimbingnight.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.config.JpaAuditingConfig;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, BaseTimeEntityAuditingTest.FixedClockConfig.class})
class BaseTimeEntityAuditingTest {

	private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired
	UserRepository userRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	Clock clock;

	@Test
	void 저장하면_생성_시각과_수정_시각이_Clock의_시각이다() {
		// given: 컨텍스트가 테스트 간에 공유되므로 현재 시각을 기준으로 삼는다
		Instant now = clock.instant();

		// when
		User saved = userRepository.saveAndFlush(User.create("audit"));

		// then
		assertThat(saved.getCreatedAt()).isEqualTo(now);
		assertThat(saved.getUpdatedAt()).isEqualTo(now);
	}

	@Test
	void 수정하면_수정_시각만_Clock의_시각으로_바뀐다() {
		// given
		Instant created = clock.instant();
		User saved = userRepository.saveAndFlush(User.create("audit2"));
		((MutableClock) clock).advance(Duration.ofMinutes(5));

		// when
		saved.changeNickname("audit3");
		userRepository.saveAndFlush(saved);

		// then
		assertThat(saved.getCreatedAt()).isEqualTo(created);
		assertThat(saved.getUpdatedAt()).isEqualTo(created.plus(Duration.ofMinutes(5)));
	}

}
