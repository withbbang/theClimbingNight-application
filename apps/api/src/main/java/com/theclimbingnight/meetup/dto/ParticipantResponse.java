package com.theclimbingnight.meetup.dto;

import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;

/** order는 확정자는 확정 순서, 대기자는 대기 순번 (각각 1부터). */
public record ParticipantResponse(String somoimNickname, MeetupApplicationStatus status, int order) {

	public static ParticipantResponse of(MeetupApplication application, int order) {
		return new ParticipantResponse(application.getRosterMember().getSomoimNickname(), application.getStatus(),
				order);
	}
}
