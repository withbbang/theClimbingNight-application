package com.theclimbingnight.meetup.repository;

import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;

public record MeetupStatusCount(long meetupId, MeetupApplicationStatus status, long count) {
}
