package com.theclimbingnight.meetup.dto;

import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;

/** waitlistPosition은 WAITLISTED일 때만 채운다 (대기 순서 appliedAt, id 기준 1부터). */
public record MyApplicationResponse(long applicationId, MeetupApplicationStatus status, Integer waitlistPosition,
		boolean lateCancel, String cancelReason) {

	public static MyApplicationResponse of(MeetupApplication application, Integer waitlistPosition) {
		return new MyApplicationResponse(application.getId(), application.getStatus(), waitlistPosition,
				application.isLateCancel(), application.getCancelReason());
	}
}
