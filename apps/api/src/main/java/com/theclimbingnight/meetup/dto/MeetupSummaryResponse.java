package com.theclimbingnight.meetup.dto;

import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.entity.MeetupStatus;
import java.time.Instant;

/** myStatus는 명부가 없거나 신청 이력이 없으면 null (취소·내보내기 이력은 그대로 나온다). */
public record MeetupSummaryResponse(long id, String title, String placeName, Instant startsAt, Instant endsAt,
		Integer capacity, long confirmedCount, long waitlistCount, MeetupStatus status,
		MeetupApplicationStatus myStatus) {

	public static MeetupSummaryResponse of(Meetup meetup, long confirmedCount, long waitlistCount,
			MeetupApplicationStatus myStatus) {
		return new MeetupSummaryResponse(meetup.getId(), meetup.getTitle(), meetup.getPlaceName(),
				meetup.getStartsAt(), meetup.getEndsAt(), meetup.getCapacity(), confirmedCount, waitlistCount,
				meetup.getStatus(), myStatus);
	}
}
