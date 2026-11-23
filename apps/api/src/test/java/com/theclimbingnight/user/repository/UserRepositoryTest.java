package com.theclimbingnight.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.user.entity.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	UserRepository userRepository;

	@Test
	void 닉네임은_중복될_수_없다() {
		// given
		userRepository.saveAndFlush(User.create("climber"));

		// when & then
		assertThatThrownBy(() -> userRepository.saveAndFlush(User.create("climber")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 탈퇴한_사용자는_여럿이어도_닉네임_충돌이_없다() {
		// given
		User a = userRepository.save(User.create("a"));
		User b = userRepository.save(User.create("b"));

		// when
		a.withdraw(Instant.parse("2026-01-01T00:00:00Z"));
		b.withdraw(Instant.parse("2026-01-01T00:00:00Z"));

		// then
		assertThatCode(() -> userRepository.flush()).doesNotThrowAnyException();
		assertThat(a.getNickname()).isNull();
	}

	@Test
	void 닉네임_존재_확인은_대소문자를_무시한다() {
		// given
		userRepository.saveAndFlush(User.create("Climber"));

		// when
		boolean exists = userRepository.existsByNicknameIgnoreCase("climber");

		// then
		assertThat(exists).isTrue();
	}
}
