package com.theclimbingnight.meetup.service;

import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴하면 아직 시작하지 않은 벙 신청을 취소하고(늦은 취소 아님), 확정자였으면 대기자를 승격한다.
 * 탈퇴 트랜잭션 안에서 동기 실행된다. 실제 취소는 {@link UpcomingApplicationCanceler}가 한다.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}로 계획 2의 {@code MembershipWithdrawalListener}보다 먼저 돈다.
 * 그 리스너가 {@code unlinkUser}로 명부 연결을 끊으면 {@code findByUserId}로 명부를 찾을 수 없고,
 * {@code clearAutomatically}가 영속성 컨텍스트를 비워 이 리스너가 읽어 둔 신청 변경도 사라질 수 있다.
 * 또 잠금 순서(사용자 → 벙 → 신청 → 명부)상 벙 잠금이 명부 갱신보다 앞서야 한다.
 */
@Component
public class MeetupWithdrawalListener {

	static final String CANCEL_REASON = "회원 탈퇴";

	private final UpcomingApplicationCanceler canceler;

	public MeetupWithdrawalListener(UpcomingApplicationCanceler canceler) {
		this.canceler = canceler;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		canceler.cancelFor(event.userId(), CANCEL_REASON);
	}
}
