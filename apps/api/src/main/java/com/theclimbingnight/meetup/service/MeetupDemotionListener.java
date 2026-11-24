package com.theclimbingnight.meetup.service;

import com.theclimbingnight.membership.service.MemberRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동호회원 자격이 해제되면 탈퇴와 똑같이 아직 시작하지 않은 벙 신청을 취소하고(늦은 취소 아님) 대기자를 승격한다.
 * 자격 해제 트랜잭션 안에서 동기 실행된다. 이미 사용자 행이 잠겨 있으므로 잠금 순서(사용자 → 벙 → 신청)가 지켜진다.
 * 명부 연결은 그대로 둔다.
 */
@Component
public class MeetupDemotionListener {

	static final String CANCEL_REASON = "동호회원 자격 해제";

	private final UpcomingApplicationCanceler canceler;

	public MeetupDemotionListener(UpcomingApplicationCanceler canceler) {
		this.canceler = canceler;
	}

	@EventListener
	@Transactional
	public void on(MemberRevokedEvent event) {
		canceler.cancelFor(event.userId(), CANCEL_REASON);
	}
}
