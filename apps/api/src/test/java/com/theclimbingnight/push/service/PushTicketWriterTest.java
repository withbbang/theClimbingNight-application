package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.push.repository.PushTicketRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** REQUIRES_NEW가 실제로 커밋되는지 보려고 테스트 자체는 트랜잭션 없이 돌리고, 커밋한 행은 직접 지운다. */
@DataJpaTest
@Import({PushTicketWriter.class, PushTokenDeleter.class, com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushTicketWriterTest {

	private static final Instant SEEN = Instant.parse("2026-11-24T10:05:00Z");

	@Autowired PushTicketWriter writer;
	@Autowired PushTokenRepository tokenRepository;
	@Autowired PushTicketRepository ticketRepository;
	@Autowired UserRepository userRepository;
	@Autowired PlatformTransactionManager txManager;

	TransactionTemplate tx;
	long userId;
	String tokenA;
	String tokenB;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(txManager);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		tokenA = "ExponentPushToken[A" + suffix + "]";
		tokenB = "ExponentPushToken[B" + suffix + "]";
		userId = tx.execute(s -> userRepository.saveAndFlush(User.create("w" + suffix)).getId());
	}

	@AfterEach
	void tearDown() {
		tx.executeWithoutResult(s -> {
			tokenRepository.deleteAllByUserId(userId);
			ticketRepository.deleteAll(ticketRepository.findAll().stream()
					.filter(t -> t.getExpoPushToken().contains(tokenA.substring(19)) || t.getTicketId().startsWith("wt-"))
					.toList());
			userRepository.deleteById(userId);
		});
	}

	private void register(String token, Instant lastSeen) {
		tx.executeWithoutResult(s -> tokenRepository.saveAndFlush(PushToken.register(userId, token, PushPlatform.IOS, lastSeen)));
	}

	private boolean exists(String token) {
		return tx.execute(s -> tokenRepository.findByExpoPushTokenForUpdate(token).isPresent());
	}

	@Test
	void 바깥_트랜잭션이_롤백돼도_티켓과_토큰_삭제는_남는다() {
		// given
		register(tokenA, SEEN);

		// when
		tx.executeWithoutResult(s -> {
			writer.saveAll(java.util.List.of(PushTicket.of("wt-1", tokenA, SEEN)));
			writer.deleteTokens(Map.of(tokenA, SEEN.plusSeconds(1)));
			s.setRollbackOnly();
		});

		// then
		assertThat(ticketRepository.findAll()).extracting(PushTicket::getTicketId).contains("wt-1");
		assertThat(exists(tokenA)).isFalse();
	}

	@Test
	void 마지막_발송_뒤에_다시_등록된_토큰은_지우지_않는다() {
		// given
		register(tokenA, SEEN.plusSeconds(1));

		// when
		Set<String> failed = writer.deleteTokens(Map.of(tokenA, SEEN));

		// then
		assertThat(failed).isEmpty();
		assertThat(exists(tokenA)).isTrue();
	}

	@Test
	void 두_번_보낸_토큰은_마지막_발송_시각으로_지운다() {
		// given: 10:00, 10:10에 발송, 10:05에 다시 등록
		register(tokenA, SEEN);

		// when
		writer.deleteTokens(Map.of(tokenA, Instant.parse("2026-11-24T10:10:00Z")));

		// then
		assertThat(exists(tokenA)).isFalse();
	}

	@Test
	void 여러_토큰을_각각_지운다() {
		// given
		register(tokenA, SEEN);
		register(tokenB, SEEN);

		// when
		Set<String> failed = writer.deleteTokens(Map.of(tokenA, SEEN, tokenB, SEEN));

		// then
		assertThat(failed).isEmpty();
		assertThat(exists(tokenA)).isFalse();
		assertThat(exists(tokenB)).isFalse();
	}
}
