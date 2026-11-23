package com.theclimbingnight.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.dto.UserResponse;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.entity.UserStatus;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock UserRepository userRepository;
	@Mock AuthIdentityRepository authIdentityRepository;
	@Mock RefreshTokenRepository refreshTokenRepository;
	@Mock ApplicationEventPublisher eventPublisher;

	private UserService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new UserService(userRepository, authIdentityRepository, refreshTokenRepository,
				new NicknamePolicy(), eventPublisher, new MutableClock(NOW));
		user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", 7L);
		given(userRepository.findById(7L)).willReturn(Optional.of(user));
		given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void 내_정보를_조회한다() {
		// when
		UserResponse response = service.getMe(7L);

		// then
		assertThat(response.id()).isEqualTo(7L);
		assertThat(response.nickname()).isEqualTo("climber");
	}

	@Test
	void 닉네임을_바꾼다() {
		// given
		given(userRepository.existsByNicknameIgnoreCase("newbie")).willReturn(false);

		// when
		UserResponse response = service.changeNickname(7L, " newbie ");

		// then
		assertThat(response.nickname()).isEqualTo("newbie");
		assertThat(user.getNickname()).isEqualTo("newbie");
	}

	@Test
	void 같은_닉네임으로_바꾸면_그대로_성공한다() {
		// given: 자기 자신과는 충돌로 보지 않는다. 대소문자만 바꾸는 것도 허용한다.
		given(userRepository.existsByNicknameIgnoreCase(any())).willReturn(true);

		// when
		UserResponse same = service.changeNickname(7L, "climber");
		UserResponse recased = service.changeNickname(7L, "Climber");

		// then
		assertThat(same.nickname()).isEqualTo("climber");
		assertThat(recased.nickname()).isEqualTo("Climber");
		assertThat(user.getNickname()).isEqualTo("Climber");
	}

	@Test
	void 다른_사람이_쓰는_닉네임이면_409다() {
		// given
		given(userRepository.existsByNicknameIgnoreCase("taken")).willReturn(true);

		// when & then
		assertThatThrownBy(() -> service.changeNickname(7L, "taken"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NICKNAME_TAKEN));
		assertThat(user.getNickname()).isEqualTo("climber");
	}

	@Test
	void 규칙에_맞지_않는_닉네임이면_400이다() {
		// when & then
		assertThatThrownBy(() -> service.changeNickname(7L, "a"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NICKNAME_INVALID));
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 동시에_같은_닉네임을_저장하면_제약_이름으로_409다() {
		// given
		given(userRepository.existsByNicknameIgnoreCase("racer")).willReturn(false);
		given(userRepository.saveAndFlush(any(User.class)))
				.willThrow(new DataIntegrityViolationException("Unique index violation: UK_USERS_NICKNAME"));

		// when & then
		assertThatThrownBy(() -> service.changeNickname(7L, "racer"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NICKNAME_TAKEN));
	}

	@Test
	void 탈퇴하면_상태와_닉네임을_지우고_소셜연결과_리프레시토큰을_정리한다() {
		// given
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user));

		// when
		service.withdraw(7L);

		// then
		assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(user.getNickname()).isNull();
		verify(authIdentityRepository).deleteByUserId(7L);
		verify(refreshTokenRepository).revokeAllActiveByUserId(7L, NOW);
		verify(eventPublisher).publishEvent(new UserWithdrawnEvent(7L));
	}

	@Test
	void 탈퇴할_때_사용자_행을_잠근다() {
		// given
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user));

		// when
		service.withdraw(7L);

		// then
		verify(userRepository).findByIdForUpdate(7L);
		verify(userRepository, never()).findById(7L);
	}

}
