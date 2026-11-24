package com.theclimbingnight.meetup.entity;

import java.time.Instant;

public record MeetupSpec(String title, String description, String placeName, Instant startsAt, Instant endsAt,
		int capacity, int lateCancelHours, boolean participantsVisible) {
}
