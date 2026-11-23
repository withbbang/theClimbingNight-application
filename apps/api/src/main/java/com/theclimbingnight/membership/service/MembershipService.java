package com.theclimbingnight.membership.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MembershipService {

	private final UserRepository userRepository;
	private final MembershipRequestRepository membershipRequestRepository;
	private final Clock clock;

	public MembershipService(UserRepository userRepository, MembershipRequestRepository membershipRequestRepository,
			Clock clock) {
		this.userRepository = userRepository;
		this.membershipRequestRepository = membershipRequestRepository;
		this.clock = clock;
	}

	/** 사용자 행 잠금이 "대기 중 신청은 한 사람당 하나"를 보장한다. 잠금 순서: 사용자 → 신청 → 명부. */
	@Transactional
	public MembershipRequestResponse apply(long userId, String somoimNickname) {
		User user = userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		if (user.getRole().atLeast(Role.MEMBER)) {
			throw new BusinessException(ErrorCode.MEMBERSHIP_ALREADY_MEMBER);
		}
		if (membershipRequestRepository.existsByUserIdAndStatus(userId, MembershipRequestStatus.PENDING)) {
			throw new BusinessException(ErrorCode.MEMBERSHIP_REQUEST_PENDING);
		}
		MembershipRequest request = membershipRequestRepository
				.save(MembershipRequest.create(user, somoimNickname.strip()));
		return MembershipRequestResponse.from(request);
	}

	public Optional<MembershipRequestResponse> getMine(long userId) {
		return membershipRequestRepository.findFirstByUserIdOrderByIdDesc(userId)
				.map(MembershipRequestResponse::from);
	}
}
