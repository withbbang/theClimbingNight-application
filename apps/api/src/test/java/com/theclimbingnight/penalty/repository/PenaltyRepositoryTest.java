package com.theclimbingnight.penalty.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PenaltyRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	PenaltyRepository penaltyRepository;

	@Autowired
	MeetupRepository meetupRepository;

	@Autowired
	RosterMemberRepository rosterMemberRepository;

	@Autowired
	UserRepository userRepository;

	private Meetup meetup(String title, Instant startsAt) {
		return meetupRepository.save(
				Meetup.create(new MeetupSpec(title, null, null, startsAt, null, 10, 24, true), null));
	}

	@Test
	void 활성_기록만_누적과_최근_3개월로_센다() {
		// given
		User admin = userRepository.save(User.create("admin"));
		RosterMember roster = rosterMemberRepository.save(RosterMember.create("r", null, RosterSource.IMPORT));
		RosterMember other = rosterMemberRepository.save(RosterMember.create("o", null, RosterSource.IMPORT));
		Instant recentFrom = NOW.minus(90, ChronoUnit.DAYS);
		Meetup old = meetup("old", recentFrom.minusSeconds(1));
		Meetup boundary = meetup("boundary", recentFrom);
		Meetup recent = meetup("recent", NOW.minus(1, ChronoUnit.DAYS));
		Meetup revokedMeetup = meetup("revoked", NOW.minus(2, ChronoUnit.DAYS));
		penaltyRepository.save(Penalty.create(roster, old, PenaltyType.NO_SHOW, admin));
		penaltyRepository.save(Penalty.create(roster, boundary, PenaltyType.LATE_CANCEL, admin));
		penaltyRepository.save(Penalty.create(roster, recent, PenaltyType.NO_SHOW, admin));
		Penalty revoked = Penalty.create(roster, revokedMeetup, PenaltyType.NO_SHOW, admin);
		revoked.revoke(admin, "사유", NOW);
		penaltyRepository.save(revoked);
		penaltyRepository.saveAndFlush(Penalty.create(other, recent, PenaltyType.NO_SHOW, admin));

		// when
		List<PenaltyCount> counts = penaltyRepository
				.countActiveByRosterIds(List.of(roster.getId(), other.getId()), recentFrom);

		// then
		assertThat(counts).containsExactlyInAnyOrder(
				new PenaltyCount(roster.getId(), 3, 2),
				new PenaltyCount(other.getId(), 1, 1));
	}
}
