package com.theclimbingnight.push.service;

import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 트랜잭션 안에서 동기 실행된다. 잠금 순서상 푸시 토큰은 마지막이다. 벌크 삭제라 영속성 컨텍스트는 그대로 둔다. */
@Component
public class PushWithdrawalListener {

	private final PushTokenRepository pushTokenRepository;
	private final NotificationSettingRepository notificationSettingRepository;

	public PushWithdrawalListener(PushTokenRepository pushTokenRepository,
			NotificationSettingRepository notificationSettingRepository) {
		this.pushTokenRepository = pushTokenRepository;
		this.notificationSettingRepository = notificationSettingRepository;
	}

	@EventListener
	@Order(Ordered.LOWEST_PRECEDENCE)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		pushTokenRepository.deleteAllByUserId(event.userId());
		notificationSettingRepository.deleteAllByUserId(event.userId());
	}
}
