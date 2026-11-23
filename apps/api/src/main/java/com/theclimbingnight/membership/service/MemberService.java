package com.theclimbingnight.membership.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.membership.dto.MemberResponse;
import com.theclimbingnight.membership.repository.MemberQueryRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberQueryRepository memberQueryRepository;
	private final UserRepository userRepository;

	public MemberService(MemberQueryRepository memberQueryRepository, UserRepository userRepository) {
		this.memberQueryRepository = memberQueryRepository;
		this.userRepository = userRepository;
	}

	public PageResponse<MemberResponse> list(String q, int page, int size) {
		String keyword = q == null || q.isBlank() ? null : q.strip();
		return PageResponse.from(memberQueryRepository.search(keyword, PageRequest.of(page, size)),
				Function.identity());
	}

	/** 활성 MEMBER만 USER로 내린다. 명부 연결은 그대로 둬 재승인 때 이어지게 한다. ADMIN은 이 API로 내리지 않는다. */
	@Transactional
	public void revoke(long userId) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (!user.isActive() || user.getRole() != Role.MEMBER) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		user.changeRole(Role.USER);
	}
}
