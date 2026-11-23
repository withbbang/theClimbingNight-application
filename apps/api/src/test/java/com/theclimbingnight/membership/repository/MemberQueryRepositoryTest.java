package com.theclimbingnight.membership.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.membership.dto.MemberResponse;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberQueryRepositoryTest {

	@Autowired
	MemberQueryRepository memberQueryRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	RosterMemberRepository rosterMemberRepository;

	private User saveUser(String nickname, Role role) {
		User user = User.create(nickname);
		user.changeRole(role);
		return userRepository.save(user);
	}

	private RosterMember link(String somoimNickname, User user) {
		RosterMember roster = RosterMember.create(somoimNickname, null, RosterSource.IMPORT);
		roster.linkTo(user);
		return rosterMemberRepository.saveAndFlush(roster);
	}

	@Test
	void MEMBER와_ADMIN만_명부_닉네임과_함께_나온다() {
		// given
		User member = saveUser("멤버가나", Role.MEMBER);
		User admin = saveUser("운영진다라", Role.ADMIN);
		User plain = saveUser("일반사용자", Role.USER);
		User withdrawn = saveUser("탈퇴예정", Role.MEMBER);
		withdrawn.withdraw(Instant.parse("2026-01-01T00:00:00Z"));
		userRepository.saveAndFlush(withdrawn);
		RosterMember roster = link("소모임멤버", member);
		link("소모임일반", plain);

		// when
		Page<MemberResponse> page = memberQueryRepository.search(null, PageRequest.of(0, 100));

		// then: H2가 다른 통합 테스트와 공유되므로 이 테스트의 사용자만 추려서 본다
		List<MemberResponse> mine = page.getContent().stream()
				.filter(row -> Set.of(member.getId(), admin.getId(), plain.getId(), withdrawn.getId())
						.contains(row.userId()))
				.toList();
		assertThat(mine).extracting(MemberResponse::userId).containsExactly(member.getId(), admin.getId());
		MemberResponse adminRow = mine.get(1);
		assertThat(adminRow.rosterMemberId()).isNull();
		assertThat(adminRow.somoimNickname()).isNull();
		MemberResponse memberRow = mine.get(0);
		assertThat(memberRow.rosterMemberId()).isEqualTo(roster.getId());
		assertThat(memberRow.somoimNickname()).isEqualTo("소모임멤버");
		assertThat(memberRow.role()).isEqualTo(Role.MEMBER);
	}

	@Test
	void 앱_닉네임이나_소모임_닉네임으로_검색한다() {
		// given
		User first = saveUser("Climber", Role.MEMBER);
		User second = saveUser("다른이름", Role.MEMBER);
		saveUser("무관한사람", Role.MEMBER);
		link("철수", first);
		link("BigWall", second);

		// when
		Page<MemberResponse> byApp = memberQueryRepository.search("climb", PageRequest.of(0, 10));
		Page<MemberResponse> bySomoim = memberQueryRepository.search("bigwall", PageRequest.of(0, 10));

		// then
		assertThat(byApp.getContent()).extracting(MemberResponse::userId).containsExactly(first.getId());
		assertThat(byApp.getTotalElements()).isEqualTo(1);
		assertThat(bySomoim.getContent()).extracting(MemberResponse::userId).containsExactly(second.getId());
	}
}
