package com.theclimbingnight.moderation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.moderation.entity.UserBlock;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserBlockRepositoryTest {

	@Autowired UserBlockRepository blocks;
	@Autowired UserRepository users;

	@Test
	void 차단은_어느_방향이든_둘_사이에_있으면_true다() {
		// given
		long a = users.saveAndFlush(User.create("가나")).getId();
		long b = users.saveAndFlush(User.create("다라")).getId();
		long c = users.saveAndFlush(User.create("마바")).getId();
		blocks.saveAndFlush(UserBlock.of(a, b, Instant.parse("2026-11-26T12:00:00Z")));

		// when / then
		assertThat(blocks.existsBetween(a, b)).isTrue();
		assertThat(blocks.existsBetween(b, a)).isTrue();
		assertThat(blocks.existsBetween(a, c)).isFalse();
	}
}
