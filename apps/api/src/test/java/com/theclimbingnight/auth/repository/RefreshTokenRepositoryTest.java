package com.theclimbingnight.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.auth.entity.RefreshToken;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EntityManager entityManager;

	private RefreshToken token(User user, String hash) {
		return refreshTokenRepository.saveAndFlush(
				RefreshToken.issue(user.getId(), hash.repeat(64).substring(0, 64), NOW.plus(Duration.ofDays(30))));
	}

	@Test
	void 조건부_폐기는_처음에만_1을_돌려준다() {
		// given
		User user = userRepository.save(User.create("a"));
		RefreshToken saved = token(user, "a");

		// when
		int first = refreshTokenRepository.revokeIfActive(saved.getId(), NOW);
		int second = refreshTokenRepository.revokeIfActive(saved.getId(), NOW.plusSeconds(5));

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isEqualTo(0);
		RefreshToken reloaded = refreshTokenRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getRevokedAt()).isEqualTo(NOW);
	}

	@Test
	void 사용자의_활성_토큰만_모두_폐기한다() {
		// given
		User a = userRepository.save(User.create("a"));
		User b = userRepository.save(User.create("b"));
		RefreshToken a1 = token(a, "1");
		RefreshToken a2 = token(a, "2");
		RefreshToken b1 = token(b, "3");

		// when
		int revoked = refreshTokenRepository.revokeAllActiveByUserId(a.getId(), NOW);
		entityManager.clear();

		// then
		assertThat(revoked).isEqualTo(2);
		assertThat(refreshTokenRepository.findById(a1.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(refreshTokenRepository.findById(a2.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(refreshTokenRepository.findById(b1.getId()).orElseThrow().getRevokedAt()).isNull();
	}
}
