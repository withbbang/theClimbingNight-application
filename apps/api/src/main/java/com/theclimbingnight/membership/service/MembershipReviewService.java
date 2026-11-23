package com.theclimbingnight.membership.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.membership.dto.MembershipApproveRequest;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.dto.MembershipReviewItemResponse;
import com.theclimbingnight.membership.dto.RosterCandidateResponse;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진의 신청 심사. 잠금 순서는 어디서나 사용자 → 신청 → 명부다(탈퇴와의 데드락 방지).
 * 신청은 잠그지 않고 신청자 ID만 읽은 뒤 사용자를 잠그고, 그다음 신청을 잠가 상태를 다시 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class MembershipReviewService {

	private final UserRepository userRepository;
	private final MembershipRequestRepository membershipRequestRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final CandidateMatcher candidateMatcher;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public MembershipReviewService(UserRepository userRepository,
			MembershipRequestRepository membershipRequestRepository, RosterMemberRepository rosterMemberRepository,
			CandidateMatcher candidateMatcher, ApplicationEventPublisher eventPublisher, Clock clock) {
		this.userRepository = userRepository;
		this.membershipRequestRepository = membershipRequestRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.candidateMatcher = candidateMatcher;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	/** id 오름차순. 후보와 일괄 승인 여부는 대기 목록에서만, 전체 대기 신청을 기준으로 계산한다. */
	public PageResponse<MembershipReviewItemResponse> list(MembershipRequestStatus status, int page, int size) {
		Page<MembershipRequest> result = membershipRequestRepository.findByStatus(status,
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
		if (status != MembershipRequestStatus.PENDING) {
			return PageResponse.from(result, request -> MembershipReviewItemResponse.of(request, List.of(), false));
		}
		Map<Long, List<RosterMember>> candidates = candidateMatcher
				.candidatesOf(membershipRequestRepository.findAllByStatusOrderByIdAsc(MembershipRequestStatus.PENDING));
		Set<Long> bulkApprovable = candidateMatcher.bulkApprovableIds(candidates);
		return PageResponse.from(result, request -> MembershipReviewItemResponse.of(request,
				candidates.getOrDefault(request.getId(), List.of()).stream().map(RosterCandidateResponse::from)
						.toList(),
				bulkApprovable.contains(request.getId())));
	}

	@Transactional
	public MembershipRequestResponse approve(long requestId, long reviewerId, MembershipApproveRequest body) {
		MembershipRequest request = lockPending(requestId);
		User applicant = request.getUser();
		Optional<RosterMember> existing = rosterMemberRepository.findByUserId(applicant.getId());

		RosterMember target;
		if (body.createNew()) {
			if (existing.isPresent()) {
				throw new BusinessException(ErrorCode.MEMBERSHIP_LINKED_ELSEWHERE);
			}
			target = RosterMember.create(request.getSomoimNickname(), null, RosterSource.APP);
			target.linkTo(applicant);
			target = rosterMemberRepository.save(target);
		} else {
			target = rosterMemberRepository.findByIdForUpdate(body.rosterMemberId())
					.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
			boolean linkedToApplicant = target.getUser() != null
					&& target.getUser().getId().equals(applicant.getId());
			if (!linkedToApplicant) {
				if (target.getUser() != null) {
					throw new BusinessException(ErrorCode.ROSTER_ALREADY_LINKED);
				}
				if (existing.isPresent()) {
					throw new BusinessException(ErrorCode.MEMBERSHIP_LINKED_ELSEWHERE);
				}
				target.linkTo(applicant);
			}
		}

		request.approve(target, userRepository.getReferenceById(reviewerId), clock.instant());
		if (applicant.getRole() == Role.USER) {
			applicant.changeRole(Role.MEMBER);
		}
		eventPublisher.publishEvent(new MembershipReviewedEvent(applicant.getId(), MembershipRequestStatus.APPROVED));
		return MembershipRequestResponse.from(request);
	}

	@Transactional
	public MembershipRequestResponse reject(long requestId, long reviewerId, String reason) {
		MembershipRequest request = lockPending(requestId);
		request.reject(reason.strip(), userRepository.getReferenceById(reviewerId), clock.instant());
		eventPublisher.publishEvent(
				new MembershipReviewedEvent(request.getUser().getId(), MembershipRequestStatus.REJECTED));
		return MembershipRequestResponse.from(request);
	}

	/** 사용자 → 신청 순서로 잠그고, 잠근 뒤 대기 상태인지 다시 확인한다. */
	private MembershipRequest lockPending(long requestId) {
		long userId = membershipRequestRepository.findUserIdById(requestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		MembershipRequest request = membershipRequestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (!request.isPending()) {
			throw new BusinessException(ErrorCode.MEMBERSHIP_REQUEST_ALREADY_REVIEWED);
		}
		return request;
	}
}
