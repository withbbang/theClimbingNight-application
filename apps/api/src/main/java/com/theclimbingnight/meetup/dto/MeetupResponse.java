package com.theclimbingnight.meetup.dto;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupStatus;
import com.theclimbingnight.user.dto.UserResponse;
import java.time.Instant;

public record MeetupResponse(long id, String title, String description, String placeName, Instant startsAt,
		Instant endsAt, Integer capacity, int lateCancelHours, boolean participantsVisible, MeetupStatus status,
		Instant canceledAt, UserResponse host) {

	public static MeetupResponse from(Meetup meetup) {
		return new MeetupResponse(meetup.getId(), meetup.getTitle(), meetup.getDescription(), meetup.getPlaceName(),
				meetup.getStartsAt(), meetup.getEndsAt(), meetup.getCapacity(), meetup.getLateCancelHours(),
				meetup.isParticipantsVisible(), meetup.getStatus(), meetup.getCanceledAt(),
				meetup.getHost() == null ? null : UserResponse.from(meetup.getHost()));
	}
}
