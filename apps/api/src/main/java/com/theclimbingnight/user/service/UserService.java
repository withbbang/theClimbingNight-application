package com.theclimbingnight.user.service;

import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.dto.UserResponse;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

	private static final String CONSTRAINT_NICKNAME = "uk_users_nickname";

	private final UserRepository userRepository;
	private final AuthIdentityRepository authIdentityRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final NicknamePolicy nicknamePolicy;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public UserService(UserRepository userRepository, AuthIdentityRepository authIdentityRepository,
			RefreshTokenRepository refreshTokenRepository, NicknamePolicy nicknamePolicy,
			ApplicationEventPublisher eventPublisher, Clock clock) {
		this.userRepository = userRepository;
		this.authIdentityRepository = authIdentityRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.nicknamePolicy = nicknamePolicy;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	public UserResponse getMe(long userId) {
		return UserResponse.from(findActive(userId));
	}

	@Transactional
	public UserResponse changeNickname(long userId, String rawNickname) {
		String nickname = nicknamePolicy.normalize(rawNickname);
		User user = findActive(userId);
		// 내 현재 닉네임과 대소문자 무시로 같으면 충돌로 보지 않는다. (대소문자만 바꾸는 변경 허용)
		boolean sameAsMine = nickname.equalsIgnoreCase(user.getNickname());
		if (!sameAsMine && userRepository.existsByNicknameIgnoreCase(nickname)) {
			throw new BusinessException(ErrorCode.USER_NICKNAME_TAKEN);
		}
		user.changeNickname(nickname);
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			if (ConstraintViolations.violated(e).contains(CONSTRAINT_NICKNAME)) {
				throw new BusinessException(ErrorCode.USER_NICKNAME_TAKEN);
			}
			throw e;
		}
		return UserResponse.from(user);
	}

	@Transactional
	public void withdraw(long userId) {
		// 승인·신청과 같은 잠금 순서(사용자 → 신청 → 명부)를 지켜, 동시 승인 결과를 낡은 상태로 덮어쓰지 않게 한다.
		User user = userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		user.withdraw(clock.instant());
		authIdentityRepository.deleteByUserId(userId);
		refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant());
		eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
	}

	private User findActive(long userId) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
	}

}
