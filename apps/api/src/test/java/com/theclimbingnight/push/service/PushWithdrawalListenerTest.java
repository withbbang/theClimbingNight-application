package com.theclimbingnight.push.service;

import static org.mockito.Mockito.inOrder;

import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushWithdrawalListenerTest {

	@Mock PushTokenRepository pushTokenRepository;
	@Mock NotificationSettingRepository notificationSettingRepository;
	@InjectMocks PushWithdrawalListener listener;

	@Test
	void 탈퇴하면_토큰과_설정을_지운다() {
		// given, when
		listener.on(new UserWithdrawnEvent(7L));

		// then
		InOrder order = inOrder(pushTokenRepository, notificationSettingRepository);
		order.verify(pushTokenRepository).deleteAllByUserId(7L);
		order.verify(notificationSettingRepository).deleteAllByUserId(7L);
	}
}
