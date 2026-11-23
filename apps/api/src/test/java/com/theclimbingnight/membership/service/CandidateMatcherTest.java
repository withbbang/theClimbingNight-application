package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CandidateMatcherTest {

	@Mock RosterMemberRepository rosterMemberRepository;

	private CandidateMatcher matcher;

	@BeforeEach
	void setUp() {
		matcher = new CandidateMatcher(rosterMemberRepository);
		given(rosterMemberRepository.findAllByUserIdIn(anyCollection())).willReturn(List.of());
		given(rosterMemberRepository.findUnlinkedByLowerNicknames(anyCollection())).willReturn(List.of());
	}

	@Test
	void 닉네임이_같은_미연결_항목이_대소문자_무관하게_후보다() {
		// given
		MembershipRequest request = request(1L, user(7L), "Chulsoo");
		RosterMember r1 = roster(11L, "chulsoo", null);
		RosterMember r2 = roster(12L, "CHULSOO", null);
		RosterMember other = roster(13L, "younghee", null);
		given(rosterMemberRepository.findUnlinkedByLowerNicknames(anyCollection())).willReturn(List.of(r1, r2, other));

		// when
		Map<Long, List<RosterMember>> candidates = matcher.candidatesOf(List.of(request));

		// then
		assertThat(candidates.get(1L)).containsExactly(r1, r2);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(rosterMemberRepository).findUnlinkedByLowerNicknames(captor.capture());
		assertThat(captor.getValue()).containsExactly("chulsoo");
	}

	@Test
	void 이미_연결된_회원은_연결된_항목만_후보다() {
		// given
		User linkedUser = user(7L);
		MembershipRequest request = request(1L, linkedUser, "철수");
		RosterMember linked = roster(15L, "옛닉네임", linkedUser);
		RosterMember sameNickname = roster(11L, "철수", null);
		given(rosterMemberRepository.findAllByUserIdIn(anyCollection())).willReturn(List.of(linked));
		given(rosterMemberRepository.findUnlinkedByLowerNicknames(anyCollection())).willReturn(List.of(sameNickname));

		// when
		Map<Long, List<RosterMember>> candidates = matcher.candidatesOf(List.of(request));

		// then
		assertThat(candidates.get(1L)).containsExactly(linked);
	}

	@Test
	void 후보가_하나뿐인_신청은_일괄_승인_대상이다() {
		// given
		Map<Long, List<RosterMember>> candidates = Map.of(
				1L, List.of(roster(11L, "철수", null)),
				2L, List.of(roster(12L, "영희", null)));

		// when
		Set<Long> ids = matcher.bulkApprovableIds(candidates);

		// then
		assertThat(ids).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void 후보가_없거나_둘_이상이면_일괄_승인_대상이_아니다() {
		// given
		Map<Long, List<RosterMember>> candidates = Map.of(
				1L, List.of(),
				2L, List.of(roster(11L, "철수", null), roster(12L, "철수", null)));

		// when
		Set<Long> ids = matcher.bulkApprovableIds(candidates);

		// then
		assertThat(ids).isEmpty();
	}

	@Test
	void 후보가_하나지만_다른_대기_신청과_겹치면_일괄_승인_대상이_아니다() {
		// given
		MembershipRequest a = request(1L, user(7L), "철수");
		MembershipRequest b = request(2L, user(8L), "철수 ");
		RosterMember r1 = roster(11L, "철수", null);
		given(rosterMemberRepository.findUnlinkedByLowerNicknames(anyCollection())).willReturn(List.of(r1));

		// when
		Map<Long, List<RosterMember>> candidates = matcher.candidatesOf(List.of(a, b));
		Set<Long> ids = matcher.bulkApprovableIds(candidates);

		// then
		assertThat(candidates.get(1L)).containsExactly(r1);
		assertThat(candidates.get(2L)).containsExactly(r1);
		assertThat(ids).isEmpty();
	}

	private static User user(long id) {
		User user = User.create("user" + id);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static MembershipRequest request(long id, User user, String nickname) {
		MembershipRequest request = MembershipRequest.create(user, nickname);
		ReflectionTestUtils.setField(request, "id", id);
		return request;
	}

	private static RosterMember roster(long id, String nickname, User linkedTo) {
		RosterMember member = RosterMember.create(nickname, null, RosterSource.IMPORT);
		ReflectionTestUtils.setField(member, "id", id);
		if (linkedTo != null) {
			member.linkTo(linkedTo);
		}
		return member;
	}
}
