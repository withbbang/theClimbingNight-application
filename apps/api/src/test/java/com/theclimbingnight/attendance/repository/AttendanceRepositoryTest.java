package com.theclimbingnight.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.entity.AttendanceResult;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupSource;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttendanceRepositoryTest {

	private static final Instant FROM = Instant.parse("2026-10-31T15:00:00Z");
	private static final Instant TO = Instant.parse("2026-11-30T15:00:00Z");
	private static final Instant RECORDED = Instant.parse("2026-12-01T00:00:00Z");

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	MeetupRepository meetupRepository;

	@Autowired
	RosterMemberRepository rosterMemberRepository;

	@Autowired
	UserRepository userRepository;

	private User admin;

	private Meetup meetup(Instant startsAt, boolean recorded) {
		Meetup meetup = Meetup.create(new MeetupSpec("벙", null, null, startsAt, null, 10, 24, true), null);
		if (recorded) {
			meetup.markAttendanceRecorded(RECORDED);
		}
		return meetupRepository.save(meetup);
	}

	private RosterMember roster(String name) {
		return rosterMemberRepository.save(RosterMember.create(name, null, RosterSource.IMPORT));
	}

	private void attend(Meetup meetup, RosterMember roster, AttendanceResult result) {
		Attendance attendance = Attendance.auto(meetup, roster);
		if (result != AttendanceResult.ATTENDED) {
			if (admin == null) {
				admin = userRepository.save(User.create("admin"));
			}
			attendance.correct(result, admin);
		}
		attendanceRepository.save(attendance);
	}

	@Test
	void 기간_시작_정각은_포함하고_끝_정각은_제외한다() {
		// given
		RosterMember me = roster("me");
		attend(meetup(FROM.minusSeconds(1), true), me, AttendanceResult.ATTENDED);
		attend(meetup(FROM, true), me, AttendanceResult.ATTENDED);
		attend(meetup(TO.minusSeconds(1), true), me, AttendanceResult.ATTENDED);
		attend(meetup(TO, true), me, AttendanceResult.ATTENDED);
		attendanceRepository.flush();

		// when, then
		assertThat(meetupRepository.countHeld(FROM, TO)).isEqualTo(2);
		assertThat(attendanceRepository.countAttended(me.getId(), FROM, TO)).isEqualTo(2);
	}

	@Test
	void 아직_기록되지_않은_벙은_분모에서_빠진다() {
		// given
		RosterMember me = roster("me");
		meetup(FROM.plusSeconds(60), false);
		attend(meetup(FROM.plusSeconds(120), true), me, AttendanceResult.ATTENDED);
		attendanceRepository.flush();

		// when, then
		assertThat(meetupRepository.countHeld(FROM, TO)).isEqualTo(1);
	}

	@Test
	void 취소된_벙은_분모와_분자에서_빠진다() {
		// given
		RosterMember me = roster("me");
		Meetup canceled = meetup(FROM.plusSeconds(60), true);
		attend(canceled, me, AttendanceResult.ATTENDED);
		canceled.cancel(RECORDED);
		attend(meetup(FROM.plusSeconds(120), true), me, AttendanceResult.ATTENDED);
		meetupRepository.flush();

		// when, then
		assertThat(meetupRepository.countHeld(FROM, TO)).isEqualTo(1);
		assertThat(attendanceRepository.countAttended(me.getId(), FROM, TO)).isEqualTo(1);
	}

	@Test
	void 이관된_벙은_분모와_분자에_들어간다() {
		// given
		RosterMember me = roster("me");
		Meetup imported = meetup(FROM.plusSeconds(60), false);
		ReflectionTestUtils.setField(imported, "source", MeetupSource.IMPORT);
		attend(imported, me, AttendanceResult.ATTENDED);
		meetupRepository.flush();

		// when, then
		assertThat(meetupRepository.countHeld(FROM, TO)).isEqualTo(1);
		assertThat(attendanceRepository.countAttended(me.getId(), FROM, TO)).isEqualTo(1);
	}

	@Test
	void 불참과_노쇼와_남의_기록은_분자에_없다() {
		// given
		RosterMember me = roster("me");
		RosterMember other = roster("other");
		attend(meetup(FROM.plusSeconds(60), true), me, AttendanceResult.EXCUSED);
		attend(meetup(FROM.plusSeconds(120), true), me, AttendanceResult.NO_SHOW);
		Meetup shared = meetup(FROM.plusSeconds(180), true);
		attend(shared, me, AttendanceResult.ATTENDED);
		attend(shared, other, AttendanceResult.ATTENDED);
		attendanceRepository.flush();

		// when, then
		assertThat(meetupRepository.countHeld(FROM, TO)).isEqualTo(3);
		assertThat(attendanceRepository.countAttended(me.getId(), FROM, TO)).isEqualTo(1);
	}
}
