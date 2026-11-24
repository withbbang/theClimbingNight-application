package com.theclimbingnight.meetup.service;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupSource;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 벙 하나의 일정 알림을 표시하고 대상 이벤트를 발행한다. 벙 행만 잠그고(잠금 순서: 벙 → 신청) 신청은 잠금 없이 읽는다.
 * 벙보다 앞선 사용자 행이나 뒤의 명부·푸시 토큰 행은 잡지 않는다 (연결된 사용자와 토큰은 커밋 뒤 푸시 쪽이 읽는다).
 */
@Service
public class MeetupReminderService {

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public MeetupReminderService(MeetupRepository meetupRepository, MeetupApplicationRepository applicationRepository,
			ApplicationEventPublisher eventPublisher, Clock clock) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	/**
	 * 벙을 잠근 뒤 후보 조건을 다시 확인하고, 맞으면 알림 시각을 표시한다. 확정자가 있으면 이벤트를 발행한다.
	 * 표시했으면 true. 그사이 취소·수정·다른 실행으로 조건이 달라졌으면 false.
	 */
	@Transactional
	public boolean remind(long meetupId, Instant until) {
		Instant now = clock.instant();
		Meetup meetup = meetupRepository.findByIdForUpdate(meetupId).orElse(null);
		if (meetup == null || !meetup.isOpen() || meetup.getSource() != MeetupSource.APP
				|| meetup.getReminderSentAt() != null || !meetup.getStartsAt().isAfter(now)
				|| !meetup.getStartsAt().isBefore(until)) {
			return false;
		}
		meetup.markReminderSent(now);
		List<Long> rosterMemberIds = applicationRepository
				.findAllByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(meetupId, MeetupApplicationStatus.CONFIRMED)
				.stream().map(MeetupApplication::getRosterMember).map(roster -> roster.getId()).toList();
		if (!rosterMemberIds.isEmpty()) {
			eventPublisher.publishEvent(new MeetupReminderDueEvent(meetupId, rosterMemberIds));
		}
		return true;
	}
}
