package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.membership.dto.MembershipApproveRequest;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.dto.MembershipReviewItemResponse;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipReviewServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final long REQUEST_ID = 10L;
	private static final long APPLICANT_ID = 7L;
	private static final long REVIEWER_ID = 1L;

	@Mock UserRepository userRepository;
	@Mock MembershipRequestRepository membershipRequestRepository;
	@Mock RosterMemberRepository rosterMemberRepository;
	@Mock CandidateMatcher candidateMatcher;
	@Mock ApplicationEventPublisher eventPublisher;

	private MembershipReviewService service;
	private User applicant;
	private User reviewer;
	private MembershipRequest request;

	@BeforeEach
	void setUp() {
		service = new MembershipReviewService(userRepository, membershipRequestRepository, rosterMemberRepository,
				candidateMatcher, eventPublisher, new MutableClock(NOW));
		applicant = user(APPLICANT_ID, Role.USER);
		reviewer = user(REVIEWER_ID, Role.ADMIN);
		request = MembershipRequest.create(applicant, "철수");
		ReflectionTestUtils.setField(request, "id", REQUEST_ID);

		given(membershipRequestRepository.findUserIdById(REQUEST_ID)).willReturn(Optional.of(APPLICANT_ID));
		given(userRepository.findByIdForUpdate(APPLICANT_ID)).willReturn(Optional.of(applicant));
		given(membershipRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request));
		given(userRepository.getReferenceById(REVIEWER_ID)).willReturn(reviewer);
		given(rosterMemberRepository.findByUserId(APPLICANT_ID)).willReturn(Optional.empty());
		given(rosterMemberRepository.save(any(RosterMember.class))).willAnswer(inv -> {
			RosterMember saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 99L);
			return saved;
		});
	}

	@Test
	void 고른_명부_항목에_연결하고_MEMBER가_된다() {
		// given
		RosterMember r1 = roster(11L, "철수", null);
		given(rosterMemberRepository.findByIdForUpdate(11L)).willReturn(Optional.of(r1));

		// when
		MembershipRequestResponse response = service.approve(REQUEST_ID, REVIEWER_ID,
				new MembershipApproveRequest(11L, false));

		// then
		assertThat(r1.getUser()).isSameAs(applicant);
		assertThat(request.getStatus()).isEqualTo(MembershipRequestStatus.APPROVED);
		assertThat(request.getRosterMember()).isSameAs(r1);
		assertThat(request.getReviewedBy()).isSameAs(reviewer);
		assertThat(request.getReviewedAt()).isEqualTo(NOW);
		assertThat(applicant.getRole()).isEqualTo(Role.MEMBER);
		assertThat(response.status()).isEqualTo(MembershipRequestStatus.APPROVED);
	}

	@Test
	void 새로_만들기로_승인하면_APP_출처_명부를_만든다() {
		// when
		service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(null, true));

		// then
		ArgumentCaptor<RosterMember> captor = ArgumentCaptor.forClass(RosterMember.class);
		verify(rosterMemberRepository).save(captor.capture());
		RosterMember created = captor.getValue();
		assertThat(created.getSomoimNickname()).isEqualTo("철수");
		assertThat(created.getSource()).isEqualTo(RosterSource.APP);
		assertThat(created.getNote()).isNull();
		assertThat(created.getUser()).isSameAs(applicant);
		assertThat(request.getRosterMember()).isSameAs(created);
	}

	@Test
	void 다른_회원에_연결된_명부를_고르면_409다() {
		// given
		RosterMember r1 = roster(11L, "철수", user(8L, Role.MEMBER));
		given(rosterMemberRepository.findByIdForUpdate(11L)).willReturn(Optional.of(r1));

		// when, then
		assertThatThrownBy(() -> service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(11L, false)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROSTER_ALREADY_LINKED));
		assertThat(request.isPending()).isTrue();
		assertThat(applicant.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void 이미_처리된_신청이면_409다() {
		for (MembershipRequestStatus status : new MembershipRequestStatus[] {
				MembershipRequestStatus.APPROVED, MembershipRequestStatus.REJECTED}) {
			// given
			ReflectionTestUtils.setField(request, "status", status);

			// when, then
			assertThatThrownBy(() -> service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(null, true)))
					.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
							.isEqualTo(ErrorCode.MEMBERSHIP_REQUEST_ALREADY_REVIEWED));
			assertThatThrownBy(() -> service.reject(REQUEST_ID, REVIEWER_ID, "사유"))
					.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
							.isEqualTo(ErrorCode.MEMBERSHIP_REQUEST_ALREADY_REVIEWED));
		}
		verify(rosterMemberRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void 자격_해제_후_재신청은_기존_명부로_이어진다() {
		// given
		RosterMember r1 = roster(11L, "철수", applicant);
		given(rosterMemberRepository.findByUserId(APPLICANT_ID)).willReturn(Optional.of(r1));
		given(rosterMemberRepository.findByIdForUpdate(11L)).willReturn(Optional.of(r1));

		// when
		service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(11L, false));

		// then
		assertThat(r1.getUser()).isSameAs(applicant);
		assertThat(request.getRosterMember()).isSameAs(r1);
		assertThat(applicant.getRole()).isEqualTo(Role.MEMBER);
		verify(rosterMemberRepository, never()).save(any());
	}

	@Test
	void 다른_명부에_연결된_회원에게_다른_항목이나_새로_만들기를_고르면_409다() {
		// given
		RosterMember linked = roster(11L, "철수", applicant);
		RosterMember another = roster(12L, "철수2", null);
		given(rosterMemberRepository.findByUserId(APPLICANT_ID)).willReturn(Optional.of(linked));
		given(rosterMemberRepository.findByIdForUpdate(12L)).willReturn(Optional.of(another));

		// when, then
		for (MembershipApproveRequest body : new MembershipApproveRequest[] {
				new MembershipApproveRequest(12L, false), new MembershipApproveRequest(null, true)}) {
			assertThatThrownBy(() -> service.approve(REQUEST_ID, REVIEWER_ID, body))
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBERSHIP_LINKED_ELSEWHERE));
		}
		assertThat(another.getUser()).isNull();
		assertThat(request.isPending()).isTrue();
		verify(rosterMemberRepository, never()).save(any());
	}

	@Test
	void 사용자_잠금이_신청_잠금보다_먼저다() {
		// given
		RosterMember r1 = roster(11L, "철수", null);
		given(rosterMemberRepository.findByIdForUpdate(11L)).willReturn(Optional.of(r1));

		// when
		service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(11L, false));

		// then
		InOrder order = inOrder(membershipRequestRepository, userRepository, rosterMemberRepository);
		order.verify(membershipRequestRepository).findUserIdById(REQUEST_ID);
		order.verify(userRepository).findByIdForUpdate(APPLICANT_ID);
		order.verify(membershipRequestRepository).findByIdForUpdate(REQUEST_ID);
		order.verify(rosterMemberRepository).findByIdForUpdate(11L);
	}

	@Test
	void 거절하면_사유를_남기고_REJECTED다() {
		// when
		MembershipRequestResponse response = service.reject(REQUEST_ID, REVIEWER_ID, "  명부에서 찾을 수 없습니다.  ");

		// then
		assertThat(request.getStatus()).isEqualTo(MembershipRequestStatus.REJECTED);
		assertThat(request.getRejectReason()).isEqualTo("명부에서 찾을 수 없습니다.");
		assertThat(request.getReviewedBy()).isSameAs(reviewer);
		assertThat(request.getReviewedAt()).isEqualTo(NOW);
		assertThat(applicant.getRole()).isEqualTo(Role.USER);
		assertThat(response.rejectReason()).isEqualTo("명부에서 찾을 수 없습니다.");
		InOrder order = inOrder(userRepository, membershipRequestRepository);
		order.verify(userRepository).findByIdForUpdate(APPLICANT_ID);
		order.verify(membershipRequestRepository).findByIdForUpdate(REQUEST_ID);
	}

	@Test
	void 승인과_거절은_심사_이벤트를_발행한다() {
		// given
		MembershipRequest second = MembershipRequest.create(applicant, "철수");
		ReflectionTestUtils.setField(second, "id", 20L);
		given(membershipRequestRepository.findUserIdById(20L)).willReturn(Optional.of(APPLICANT_ID));
		given(membershipRequestRepository.findByIdForUpdate(20L)).willReturn(Optional.of(second));

		// when
		service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(null, true));
		service.reject(20L, REVIEWER_ID, "사유");

		// then
		InOrder order = inOrder(eventPublisher);
		order.verify(eventPublisher)
				.publishEvent(new MembershipReviewedEvent(APPLICANT_ID, MembershipRequestStatus.APPROVED));
		order.verify(eventPublisher)
				.publishEvent(new MembershipReviewedEvent(APPLICANT_ID, MembershipRequestStatus.REJECTED));
	}

	@Test
	void ADMIN이_승인되면_역할을_내리지_않는다() {
		// given
		applicant.changeRole(Role.ADMIN);

		// when
		service.approve(REQUEST_ID, REVIEWER_ID, new MembershipApproveRequest(null, true));

		// then
		assertThat(applicant.getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void 없는_신청을_심사하면_404다() {
		// given
		given(membershipRequestRepository.findUserIdById(404L)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.reject(404L, REVIEWER_ID, "사유"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		verify(userRepository, never()).findByIdForUpdate(any(Long.class));
	}

	@Test
	void 대기_목록은_전체_대기_신청_기준으로_후보와_일괄승인_여부를_채운다() {
		// given
		RosterMember r1 = roster(11L, "철수", null);
		Pageable pageable = PageRequest.of(0, 20);
		given(membershipRequestRepository.findByStatus(any(), any()))
				.willReturn(new PageImpl<>(List.of(request), pageable, 1));
		given(membershipRequestRepository.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING))
				.willReturn(List.of(request));
		given(candidateMatcher.candidatesOf(List.of(request))).willReturn(Map.of(REQUEST_ID, List.of(r1)));
		given(candidateMatcher.bulkApprovableIds(anyMap())).willReturn(Set.of(REQUEST_ID));

		// when
		PageResponse<MembershipReviewItemResponse> page = service.list(MembershipRequestStatus.PENDING, 0, 20);

		// then
		assertThat(page.totalElements()).isEqualTo(1L);
		MembershipReviewItemResponse item = page.items().get(0);
		assertThat(item.user().id()).isEqualTo(APPLICANT_ID);
		assertThat(item.candidates()).extracting(c -> c.id()).containsExactly(11L);
		assertThat(item.bulkApprovable()).isTrue();
	}

	@Test
	void 대기가_아닌_목록은_후보가_비고_일괄승인_대상이_아니다() {
		// given
		ReflectionTestUtils.setField(request, "status", MembershipRequestStatus.REJECTED);
		given(membershipRequestRepository.findByStatus(any(), any()))
				.willReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));

		// when
		PageResponse<MembershipReviewItemResponse> page = service.list(MembershipRequestStatus.REJECTED, 0, 20);

		// then
		MembershipReviewItemResponse item = page.items().get(0);
		assertThat(item.candidates()).isEmpty();
		assertThat(item.bulkApprovable()).isFalse();
		verify(candidateMatcher, never()).candidatesOf(any());
	}

	private static User user(long id, Role role) {
		User user = User.create("user" + id);
		ReflectionTestUtils.setField(user, "id", id);
		user.changeRole(role);
		return user;
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
