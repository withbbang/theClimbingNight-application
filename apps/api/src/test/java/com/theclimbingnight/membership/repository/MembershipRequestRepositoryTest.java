package com.theclimbingnight.membership.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MembershipRequestRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	MembershipRequestRepository membershipRequestRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EntityManager entityManager;

	@Test
	void 사용자의_가장_최근_신청을_찾는다() {
		// given
		User user = userRepository.save(User.create("u"));
		MembershipRequest old = MembershipRequest.create(user, "old");
		old.reject("사유", null, NOW);
		membershipRequestRepository.save(old);
		MembershipRequest latest = membershipRequestRepository.saveAndFlush(MembershipRequest.create(user, "latest"));

		// when
		var found = membershipRequestRepository.findFirstByUserIdOrderByIdDesc(user.getId());

		// then
		assertThat(found).isPresent().get().extracting(MembershipRequest::getId).isEqualTo(latest.getId());
	}

	@Test
	void 상태별로_오래된_순서로_찾는다() {
		// given
		User a = userRepository.save(User.create("a"));
		User b = userRepository.save(User.create("b"));
		User c = userRepository.save(User.create("c"));
		membershipRequestRepository.save(MembershipRequest.create(a, "first"));
		MembershipRequest done = MembershipRequest.create(b, "done");
		done.reject("사유", null, NOW);
		membershipRequestRepository.save(done);
		membershipRequestRepository.saveAndFlush(MembershipRequest.create(c, "third"));

		// when
		List<MembershipRequest> pending = membershipRequestRepository
				.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING);

		// then
		assertThat(pending).extracting(MembershipRequest::getSomoimNickname).containsExactly("first", "third");
	}

	@Test
	void 신청자_ID만_읽고_신청_엔티티는_올리지_않는다() {
		// given
		User user = userRepository.save(User.create("u"));
		MembershipRequest saved = membershipRequestRepository.saveAndFlush(MembershipRequest.create(user, "철수"));
		entityManager.clear();

		// when
		var userId = membershipRequestRepository.findUserIdById(saved.getId());

		// then
		assertThat(userId).contains(user.getId());
		assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
				.isLoaded(entityManager.getReference(MembershipRequest.class, saved.getId()))).isFalse();
		assertThat(membershipRequestRepository.findUserIdById(saved.getId() + 1000)).isEmpty();
	}
}
