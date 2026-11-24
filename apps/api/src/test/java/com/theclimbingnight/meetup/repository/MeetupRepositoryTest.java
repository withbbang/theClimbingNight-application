package com.theclimbingnight.meetup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeetupRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	MeetupRepository meetupRepository;

	private Meetup meetup(String title, Instant startsAt, Instant endsAt) {
		return Meetup.create(new MeetupSpec(title, null, null, startsAt, endsAt, 10, 24, true), null);
	}

	@Test
	void 기간_안에_시작하는_벙만_시작_순서로_나온다() {
		// given
		Instant from = NOW;
		Instant to = NOW.plus(1, ChronoUnit.DAYS);
		meetupRepository.save(meetup("before", from.minusSeconds(1), null));
		Meetup atTo = meetup("atTo", to, null);
		meetupRepository.save(atTo);
		meetupRepository.save(meetup("later", from.plusSeconds(60), null));
		meetupRepository.save(meetup("atFrom", from, null));
		meetupRepository.flush();

		// when
		List<Meetup> found = meetupRepository
				.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(from, to);

		// then
		assertThat(found).extracting(Meetup::getTitle).containsExactly("atFrom", "later");
	}

	@Test
	void 끝났고_기록_전인_앱_벙만_자동_기록_대상이다() {
		// given
		Instant start = NOW.minus(5, ChronoUnit.HOURS);
		Meetup due = meetupRepository.save(meetup("due", start, NOW.minusSeconds(10)));
		Meetup exactlyNow = meetupRepository.save(meetup("exactlyNow", start, NOW));
		meetupRepository.save(meetup("notEnded", start, NOW.plusSeconds(1)));
		Meetup canceled = meetup("canceled", start, NOW.minusSeconds(10));
		canceled.cancel(NOW);
		meetupRepository.save(canceled);
		Meetup imported = meetup("imported", start, NOW.minusSeconds(10));
		ReflectionTestUtils.setField(imported, "source", com.theclimbingnight.meetup.entity.MeetupSource.IMPORT);
		meetupRepository.save(imported);
		Meetup recorded = meetup("recorded", start, NOW.minusSeconds(10));
		recorded.markAttendanceRecorded(NOW);
		meetupRepository.save(recorded);
		meetupRepository.flush();

		// when
		List<Long> ids = meetupRepository.findAttendanceDueIds(NOW);

		// then
		assertThat(ids).containsExactly(due.getId(), exactlyNow.getId());
	}

	@Test
	void 알리지_않은_열린_앱_벙만_시작_시각_경계_안에서_일정_알림_대상이다() {
		// given
		Instant until = NOW.plus(1, ChronoUnit.DAYS);
		meetupRepository.save(meetup("atNow", NOW, null));
		Meetup first = meetupRepository.save(meetup("first", NOW.plusSeconds(1), null));
		Meetup last = meetupRepository.save(meetup("last", until.minusSeconds(1), null));
		meetupRepository.save(meetup("atUntil", until, null));
		Meetup canceled = meetup("canceled", NOW.plusSeconds(60), null);
		canceled.cancel(NOW);
		meetupRepository.save(canceled);
		Meetup imported = meetup("imported", NOW.plusSeconds(60), null);
		ReflectionTestUtils.setField(imported, "source", com.theclimbingnight.meetup.entity.MeetupSource.IMPORT);
		meetupRepository.save(imported);
		Meetup sent = meetup("sent", NOW.plusSeconds(60), null);
		sent.markReminderSent(NOW);
		meetupRepository.save(sent);
		meetupRepository.flush();

		// when, then
		assertThat(meetupRepository.findReminderDueIds(NOW, until)).containsExactly(first.getId(), last.getId());
	}
}
