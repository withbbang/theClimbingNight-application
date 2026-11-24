package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushTokenWriterTest {

	private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");
	private static final Instant T1 = Instant.parse("2026-06-02T00:00:00Z");
	private static final String TOKEN = "ExponentPushToken[abc]";

	@Mock UserRepository userRepository;
	@Mock PushTokenRepository pushTokenRepository;

	private PushTokenWriter writer() {
		return new PushTokenWriter(userRepository, pushTokenRepository, new MutableClock(T1));
	}

	private User user(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void 같은_토큰을_다른_사용자가_등록하면_소유자가_바뀐다() {
		// given
		PushToken existing = PushToken.register(1L, TOKEN, PushPlatform.IOS, T0);
		given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(user(2L)));
		given(pushTokenRepository.findByExpoPushToken(TOKEN)).willReturn(Optional.of(existing));

		// when
		writer().upsert(2L, TOKEN, PushPlatform.ANDROID);

		// then
		assertThat(existing.getUserId()).isEqualTo(2L);
		assertThat(existing.getPlatform()).isEqualTo(PushPlatform.ANDROID);
		assertThat(existing.getLastSeenAt()).isEqualTo(T1);
		verify(pushTokenRepository, never()).saveAndFlush(any());
	}

	@Test
	void 없는_토큰은_사용자_잠금_뒤_새로_저장한다() {
		// given
		given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(user(2L)));
		given(pushTokenRepository.findByExpoPushToken(TOKEN)).willReturn(Optional.empty());

		// when
		writer().upsert(2L, TOKEN, PushPlatform.IOS);

		// then
		InOrder order = Mockito.inOrder(userRepository, pushTokenRepository);
		order.verify(userRepository).findByIdForUpdate(2L);
		order.verify(pushTokenRepository).saveAndFlush(any(PushToken.class));
	}

	@Test
	void 탈퇴한_사용자는_등록할_수_없다() {
		// given
		User left = user(2L);
		left.withdraw(T0);
		given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(left));

		// when, then
		assertThatThrownBy(() -> writer().upsert(2L, TOKEN, PushPlatform.IOS))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(pushTokenRepository, never()).saveAndFlush(any());
	}
}
