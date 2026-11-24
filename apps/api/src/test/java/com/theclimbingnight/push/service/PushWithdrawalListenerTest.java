package com.theclimbingnight.push.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushWithdrawalListenerTest {

	@Mock PushTokenRepository pushTokenRepository;
	@Mock NotificationSettingRepository notificationSettingRepository;
	@InjectMocks PushWithdrawalListener listener;

	private static PushToken token(long id, long userId, String value) {
		PushToken token = PushToken.register(userId, value, PushPlatform.IOS, Instant.parse("2026-06-01T00:00:00Z"));
		ReflectionTestUtils.setField(token, "id", id);
		return token;
	}

	@Test
	void 탈퇴하면_토큰을_토큰_값으로_잠근_뒤_지우고_설정을_지운다() {
		// given
		given(pushTokenRepository.findTokensOwnedBy(7L)).willReturn(List.of("A", "B"));
		given(pushTokenRepository.findByExpoPushTokenForUpdate("A")).willReturn(Optional.of(token(1L, 7L, "A")));
		given(pushTokenRepository.findByExpoPushTokenForUpdate("B")).willReturn(Optional.of(token(2L, 7L, "B")));

		// when
		listener.on(new UserWithdrawnEvent(7L));

		// then
		InOrder order = inOrder(pushTokenRepository, notificationSettingRepository);
		order.verify(pushTokenRepository).findByExpoPushTokenForUpdate("A");
		order.verify(pushTokenRepository).deleteAllByIdInBatch(List.of(1L));
		order.verify(pushTokenRepository).findByExpoPushTokenForUpdate("B");
		order.verify(pushTokenRepository).deleteAllByIdInBatch(List.of(2L));
		order.verify(notificationSettingRepository).deleteAllByUserId(7L);
	}

	@Test
	void 잠그기_전에_다른_사용자가_이어받았거나_사라진_토큰은_건드리지_않는다() {
		// given
		given(pushTokenRepository.findTokensOwnedBy(7L)).willReturn(List.of("A", "B"));
		given(pushTokenRepository.findByExpoPushTokenForUpdate("A")).willReturn(Optional.of(token(1L, 8L, "A")));
		given(pushTokenRepository.findByExpoPushTokenForUpdate("B")).willReturn(Optional.empty());

		// when
		listener.on(new UserWithdrawnEvent(7L));

		// then
		verify(pushTokenRepository, never()).deleteAllByIdInBatch(org.mockito.ArgumentMatchers.any());
		verify(notificationSettingRepository).deleteAllByUserId(7L);
	}
}
