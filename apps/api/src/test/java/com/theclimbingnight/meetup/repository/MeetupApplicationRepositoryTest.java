package com.theclimbingnight.meetup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeetupApplicationRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	MeetupApplicationRepository meetupApplicationRepository;

	@Autowired
	MeetupRepository meetupRepository;

	@Autowired
	RosterMemberRepository rosterMemberRepository;

	@Autowired
	EntityManager entityManager;

	private Meetup meetup(String title, Instant startsAt) {
		return meetupRepository.save(
				Meetup.create(new MeetupSpec(title, null, null, startsAt, null, 10, 24, true), null));
	}

	private RosterMember roster(String nickname) {
		return rosterMemberRepository.save(RosterMember.create(nickname, null, RosterSource.IMPORT));
	}

	@Test
	void 같은_벙에_같은_명부로_두_번_신청하면_유니크_위반이다() {
		// given
		Meetup meetup = meetup("m", NOW.plus(1, ChronoUnit.DAYS));
		RosterMember roster = roster("r");
		meetupApplicationRepository.saveAndFlush(MeetupApplication.create(meetup, roster, true, NOW));

		// when, then
		assertThatThrownBy(() -> meetupApplicationRepository
				.saveAndFlush(MeetupApplication.create(meetup, roster, true, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(e -> assertThat(ConstraintViolations.violated(e))
						.contains("uk_meetup_applications_meetup_roster"));
	}

	@Test
	void 확정과_대기만_벙별로_센다() {
		// given
		Meetup a = meetup("a", NOW.plus(1, ChronoUnit.DAYS));
		Meetup b = meetup("b", NOW.plus(2, ChronoUnit.DAYS));
		meetupApplicationRepository.save(MeetupApplication.create(a, roster("r1"), true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(a, roster("r2"), true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(a, roster("r3"), false, NOW));
		MeetupApplication canceled = MeetupApplication.create(a, roster("r4"), true, NOW);
		canceled.cancel(false, null, NOW);
		meetupApplicationRepository.save(canceled);
		MeetupApplication removed = MeetupApplication.create(a, roster("r5"), true, NOW);
		removed.remove(NOW);
		meetupApplicationRepository.save(removed);
		meetupApplicationRepository.saveAndFlush(MeetupApplication.create(b, roster("r6"), false, NOW));

		// when
		List<MeetupStatusCount> counts = meetupApplicationRepository.countByMeetupIds(List.of(a.getId(), b.getId()));

		// then
		assertThat(counts).containsExactlyInAnyOrder(
				new MeetupStatusCount(a.getId(), MeetupApplicationStatus.CONFIRMED, 2),
				new MeetupStatusCount(a.getId(), MeetupApplicationStatus.WAITLISTED, 1),
				new MeetupStatusCount(b.getId(), MeetupApplicationStatus.WAITLISTED, 1));
	}

	@Test
	void 다가오는_활성_신청만_찾는다() {
		// given
		RosterMember me = roster("me");
		Meetup upcoming = meetup("upcoming", NOW.plus(1, ChronoUnit.DAYS));
		Meetup waiting = meetup("waiting", NOW.plus(2, ChronoUnit.DAYS));
		Meetup past = meetup("past", NOW.minus(1, ChronoUnit.DAYS));
		Meetup startedNow = meetup("startedNow", NOW);
		Meetup canceledMeetup = meetup("canceledMeetup", NOW.plus(3, ChronoUnit.DAYS));
		canceledMeetup.cancel(NOW);
		Meetup canceledApplication = meetup("canceledApplication", NOW.plus(4, ChronoUnit.DAYS));
		meetupApplicationRepository.save(MeetupApplication.create(upcoming, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(waiting, me, false, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(past, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(startedNow, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(canceledMeetup, me, true, NOW));
		MeetupApplication canceled = MeetupApplication.create(canceledApplication, me, true, NOW);
		canceled.cancel(false, null, NOW);
		meetupApplicationRepository.save(canceled);
		meetupApplicationRepository.save(MeetupApplication.create(upcoming, roster("other"), true, NOW));
		meetupApplicationRepository.flush();

		// when
		List<MeetupApplication> found = meetupApplicationRepository.findActiveUpcoming(me.getId(), NOW);

		// then
		assertThat(found).extracting(a -> a.getMeetup().getTitle()).containsExactlyInAnyOrder("upcoming", "waiting");
	}

	@Test
	void 다가오는_활성_신청의_벙_ID를_오름차순으로_찾는다() {
		// given
		RosterMember me = roster("me");
		Meetup later = meetup("later", NOW.plus(2, ChronoUnit.DAYS));
		Meetup sooner = meetup("sooner", NOW.plus(1, ChronoUnit.DAYS));
		Meetup past = meetup("past", NOW.minus(1, ChronoUnit.DAYS));
		Meetup startedNow = meetup("startedNow", NOW);
		Meetup canceledMeetup = meetup("canceledMeetup", NOW.plus(3, ChronoUnit.DAYS));
		canceledMeetup.cancel(NOW);
		Meetup removedApplication = meetup("removedApplication", NOW.plus(4, ChronoUnit.DAYS));
		meetupApplicationRepository.save(MeetupApplication.create(later, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(sooner, me, false, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(past, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(startedNow, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(canceledMeetup, me, true, NOW));
		MeetupApplication removed = MeetupApplication.create(removedApplication, me, true, NOW);
		removed.remove(NOW);
		meetupApplicationRepository.save(removed);
		Meetup othersOnly = meetup("othersOnly", NOW.plus(5, ChronoUnit.DAYS));
		meetupApplicationRepository.save(MeetupApplication.create(othersOnly, roster("other"), true, NOW));
		meetupApplicationRepository.flush();

		// when
		List<Long> ids = meetupApplicationRepository.findActiveUpcomingMeetupIds(me.getId(), NOW);

		// then
		assertThat(ids).containsExactly(later.getId(), sooner.getId());
	}

	@Test
	void 잠금_조회는_명부와_벙으로_신청을_찾는다() {
		// given
		RosterMember me = roster("me");
		Meetup meetup = meetup("m", NOW.plus(1, ChronoUnit.DAYS));
		MeetupApplication mine = meetupApplicationRepository.save(MeetupApplication.create(meetup, me, true, NOW));
		meetupApplicationRepository.save(MeetupApplication.create(meetup, roster("other"), true, NOW));
		meetupApplicationRepository.flush();
		entityManager.clear();

		// when, then
		assertThat(meetupApplicationRepository.findByMeetupIdAndRosterMemberIdForUpdate(meetup.getId(), me.getId()))
				.get().extracting(MeetupApplication::getId).isEqualTo(mine.getId());
		assertThat(meetupApplicationRepository.findByMeetupIdAndRosterMemberIdForUpdate(meetup.getId(), -1L))
				.isEmpty();
	}

	/** 탈퇴 리스너가 엔티티 대신 벙 ID만 먼저 조회하는 이유: 이미 올라온 엔티티는 잠금 조회로도 새로 읽히지 않는다. */
	@Test
	void 이미_읽어_둔_신청은_잠금_조회로도_새로_고쳐지지_않는다() {
		// given
		RosterMember me = roster("me");
		Meetup meetup = meetup("m", NOW.plus(1, ChronoUnit.DAYS));
		MeetupApplication mine = meetupApplicationRepository.save(MeetupApplication.create(meetup, me, true, NOW));
		meetupApplicationRepository.flush();
		entityManager.clear();
		meetupApplicationRepository.findActiveUpcoming(me.getId(), NOW);
		// 다른 트랜잭션이 내보낸 것처럼 영속성 컨텍스트를 거치지 않고 바꾼다.
		entityManager.createNativeQuery("update meetup_applications set status = 'REMOVED' where id = :id")
				.setParameter("id", mine.getId()).executeUpdate();

		// when
		MeetupApplication relocked = meetupApplicationRepository
				.findByMeetupIdAndRosterMemberIdForUpdate(meetup.getId(), me.getId()).orElseThrow();

		// then
		assertThat(relocked.getStatus()).isEqualTo(MeetupApplicationStatus.CONFIRMED);
		entityManager.clear();
		assertThat(meetupApplicationRepository.findByMeetupIdAndRosterMemberIdForUpdate(meetup.getId(), me.getId())
				.orElseThrow().getStatus()).isEqualTo(MeetupApplicationStatus.REMOVED);
	}
}
