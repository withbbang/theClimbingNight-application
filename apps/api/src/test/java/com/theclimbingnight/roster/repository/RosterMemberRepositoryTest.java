package com.theclimbingnight.roster.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RosterMemberRepositoryTest {

	@Autowired
	RosterMemberRepository rosterMemberRepository;

	@Autowired
	UserRepository userRepository;

	private RosterMember member(String nickname) {
		return RosterMember.create(nickname, null, RosterSource.IMPORT);
	}

	@Test
	void 한_계정은_명부_항목_하나에만_연결된다() {
		// given
		User user = userRepository.save(User.create("u"));
		RosterMember first = member("first");
		first.linkTo(user);
		rosterMemberRepository.saveAndFlush(first);
		RosterMember second = member("second");
		second.linkTo(user);

		// when, then
		assertThatThrownBy(() -> rosterMemberRepository.saveAndFlush(second))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(e -> assertThat(ConstraintViolations.violated(e)).contains("uk_roster_members_user"));
	}

	@Test
	void 대소문자와_무관하게_미연결_항목만_찾는다() {
		// given
		User user = userRepository.save(User.create("u"));
		rosterMemberRepository.save(member("Climber"));
		RosterMember linked = member("climber");
		linked.linkTo(user);
		rosterMemberRepository.save(linked);
		rosterMemberRepository.saveAndFlush(member("other"));

		// when
		List<RosterMember> found = rosterMemberRepository.findUnlinkedByLowerNicknames(List.of("climber"));

		// then
		assertThat(found).extracting(RosterMember::getSomoimNickname).containsExactly("Climber");
	}

	@Test
	void 이미_있는_닉네임을_소문자로_돌려준다() {
		// given
		rosterMemberRepository.saveAndFlush(member("Alpha"));

		// when
		List<String> existing = rosterMemberRepository.findExistingLowerNicknames(List.of("alpha", "beta"));

		// then
		assertThat(existing).containsExactly("alpha");
	}

	@Test
	void 탈퇴_회원의_연결을_끊어도_명부_항목은_남는다() {
		// given
		User user = userRepository.save(User.create("u"));
		RosterMember linked = member("climber");
		linked.linkTo(user);
		RosterMember saved = rosterMemberRepository.saveAndFlush(linked);

		// when
		int updated = rosterMemberRepository.unlinkUser(user.getId());

		// then
		assertThat(updated).isEqualTo(1);
		assertThat(rosterMemberRepository.findById(saved.getId())).isPresent()
				.get().extracting(RosterMember::getUser).isNull();
	}

	@Test
	void 명부_검색은_부분일치와_연결여부로_거른다() {
		// given
		User user = userRepository.save(User.create("u"));
		RosterMember kim = member("김철수");
		kim.linkTo(user);
		rosterMemberRepository.save(kim);
		rosterMemberRepository.save(member("박철수"));
		rosterMemberRepository.saveAndFlush(member("영희"));

		// when
		var unlinkedMatch = rosterMemberRepository.search("철수", false, PageRequest.of(0, 10));
		var all = rosterMemberRepository.search(null, null, PageRequest.of(0, 10));

		// then
		assertThat(unlinkedMatch.getContent()).extracting(RosterMember::getSomoimNickname).containsExactly("박철수");
		assertThat(all.getTotalElements()).isEqualTo(3);
	}
}
