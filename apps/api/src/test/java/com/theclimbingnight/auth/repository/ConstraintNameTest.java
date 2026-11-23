package com.theclimbingnight.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

/** 실제 DB(H2 MySQL 모드)가 위반한 제약 이름을 원인 사슬에 싣는지 확인한다. AuthService의 분기가 이에 기댄다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConstraintNameTest {

	@Autowired
	AuthIdentityRepository authIdentityRepository;

	@Autowired
	UserRepository userRepository;

	private static String chain(Throwable e) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
				sb.append("[name=").append(cve.getConstraintName()).append("] ");
			}
			sb.append(t.getMessage()).append(' ');
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	@Test
	void 닉네임_중복이면_원인_사슬에_닉네임_제약_이름이_있다() {
		// given
		userRepository.saveAndFlush(User.create("climber"));

		// when, then
		assertThatThrownBy(() -> userRepository.saveAndFlush(User.create("climber")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(e -> assertThat(chain(e)).contains("uk_users_nickname"));
	}

	@Test
	void 소셜계정_중복이면_원인_사슬에_연결_제약_이름이_있다() {
		// given
		User a = userRepository.saveAndFlush(User.create("a"));
		User b = userRepository.saveAndFlush(User.create("b"));
		authIdentityRepository.saveAndFlush(AuthIdentity.link(a, SocialProvider.KAKAO, "123"));

		// when, then
		assertThatThrownBy(() -> authIdentityRepository
				.saveAndFlush(AuthIdentity.link(b, SocialProvider.KAKAO, "123")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(e -> assertThat(chain(e)).contains("uk_auth_identities_provider"));
	}

}
