package com.theclimbingnight.meetup.service;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import org.springframework.stereotype.Component;

/** 벙의 빈 자리 수. 호출자가 벙 잠금을 잡고 있어야 값이 정확하다. */
@Component
public class SeatCounter {

	private final MeetupApplicationRepository applicationRepository;

	public SeatCounter(MeetupApplicationRepository applicationRepository) {
		this.applicationRepository = applicationRepository;
	}

	/** 정원이 없는 벙(가져온 기록)은 {@link Long#MAX_VALUE}, 아니면 정원에서 확정자를 뺀 값(0 미만이면 0). */
	public long freeSeats(Meetup meetup) {
		if (meetup.getCapacity() == null) {
			return Long.MAX_VALUE;
		}
		long confirmed = applicationRepository.countByMeetupIdAndStatus(meetup.getId(),
				MeetupApplicationStatus.CONFIRMED);
		return Math.max(0, meetup.getCapacity() - confirmed);
	}
}
