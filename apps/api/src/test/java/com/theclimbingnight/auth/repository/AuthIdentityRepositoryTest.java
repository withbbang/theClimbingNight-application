package com.theclimbingnight.auth.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthIdentityRepositoryTest {

	@Autowired
	AuthIdentityRepository authIdentityRepository;

	@Autowired
	UserRepository userRepository;

	@Test
	void 같은_제공자의_같은_사용자ID는_한번만_연결된다() {
		// given
		User a = userRepository.save(User.create("a"));
		User b = userRepository.save(User.create("b"));
		authIdentityRepository.saveAndFlush(AuthIdentity.link(a, SocialProvider.KAKAO, "123"));

		// when & then
		assertThatThrownBy(() -> authIdentityRepository
				.saveAndFlush(AuthIdentity.link(b, SocialProvider.KAKAO, "123")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 제공자가_다르면_같은_ID도_연결된다() {
		// given
		User a = userRepository.save(User.create("a"));
		authIdentityRepository.saveAndFlush(AuthIdentity.link(a, SocialProvider.KAKAO, "123"));

		// when & then
		assertThatCode(() -> authIdentityRepository
				.saveAndFlush(AuthIdentity.link(a, SocialProvider.NAVER, "123")))
				.doesNotThrowAnyException();
	}
}
