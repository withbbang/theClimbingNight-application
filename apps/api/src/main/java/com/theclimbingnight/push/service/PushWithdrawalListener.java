package com.theclimbingnight.push.service;

import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.push.repository.PushTicketRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 트랜잭션 안에서 동기 실행된다. 잠금 순서상 푸시 토큰은 마지막이다. 벌크 삭제라 영속성 컨텍스트는 그대로 둔다.
 * <p>사용자 인덱스로 한꺼번에 지우면 등록(토큰 유니크 인덱스 → 행 → 사용자 인덱스)과 잠금 순서가 거꾸로라 교착한다.
 * 그래서 토큰 값으로 하나씩 잠그고 지운다. 사용자 행이 잠겨 있어 이 사용자의 토큰이 그사이 늘어나지는 않는다.
 */
@Component
public class PushWithdrawalListener {

	private final PushTokenRepository pushTokenRepository;
	private final PushTicketRepository pushTicketRepository;
	private final NotificationSettingRepository notificationSettingRepository;

	public PushWithdrawalListener(PushTokenRepository pushTokenRepository,
			PushTicketRepository pushTicketRepository, NotificationSettingRepository notificationSettingRepository) {
		this.pushTokenRepository = pushTokenRepository;
		this.pushTicketRepository = pushTicketRepository;
		this.notificationSettingRepository = notificationSettingRepository;
	}

	@EventListener
	@Order(Ordered.LOWEST_PRECEDENCE)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		List<String> deleted = new ArrayList<>();
		for (String token : pushTokenRepository.findTokensOwnedBy(event.userId())) {
			pushTokenRepository.findByExpoPushTokenForUpdate(token)
					.filter(locked -> locked.getUserId() == event.userId())
					.ifPresent(locked -> {
						pushTokenRepository.deleteAllByIdInBatch(List.of(locked.getId()));
						deleted.add(token);
					});
		}
		List<Long> ticketIds = deleted.isEmpty() ? List.of() : pushTicketRepository.findIdsByTokens(deleted);
		if (!ticketIds.isEmpty()) {
			pushTicketRepository.deleteAllByIdInBatch(ticketIds);
		}
		notificationSettingRepository.deleteAllByUserId(event.userId());
	}
}
