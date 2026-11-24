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
}
