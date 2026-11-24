package com.theclimbingnight.meetup.service;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 빈 자리만큼 대기자를 대기 순서대로 확정한다.
 * 호출자가 벙 잠금(findByIdForUpdate)을 잡고 있어야 한다. 잠금 없이 부르면 정원을 넘겨 확정할 수 있다.
 */
@Component
public class WaitlistPromoter {

	private final MeetupApplicationRepository applicationRepository;
	private final ApplicationEventPublisher eventPublisher;

	public WaitlistPromoter(MeetupApplicationRepository applicationRepository,
			ApplicationEventPublisher eventPublisher) {
		this.applicationRepository = applicationRepository;
		this.eventPublisher = eventPublisher;
	}

	public List<MeetupApplication> promote(Meetup meetup, Instant now) {
		List<MeetupApplication> promoted = new ArrayList<>();
		if (meetup.getCapacity() == null) {
			return promoted;
		}
		long free = meetup.getCapacity()
				- applicationRepository.countByMeetupIdAndStatus(meetup.getId(), MeetupApplicationStatus.CONFIRMED);
		if (free <= 0) {
			return promoted;
		}
		List<MeetupApplication> waiting = applicationRepository
				.findAllByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(meetup.getId(),
						MeetupApplicationStatus.WAITLISTED);
		for (MeetupApplication application : waiting) {
			if (promoted.size() >= free) {
				break;
			}
			application.confirm(now);
			promoted.add(application);
			eventPublisher.publishEvent(
					new MeetupApplicationPromotedEvent(meetup.getId(), application.getRosterMember().getId()));
		}
		return promoted;
	}
}
