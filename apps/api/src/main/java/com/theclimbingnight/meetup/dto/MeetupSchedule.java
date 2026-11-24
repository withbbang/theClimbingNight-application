package com.theclimbingnight.meetup.dto;

import java.time.Duration;
import java.time.OffsetDateTime;

/** 요청 DTO들이 공유하는 일정 검증. null은 @NotNull이 잡으므로 여기서는 통과시킨다. */
final class MeetupSchedule {

	private static final Duration MAX_LENGTH = Duration.ofHours(24);

	private MeetupSchedule() {
	}

	static boolean isValid(OffsetDateTime startsAt, OffsetDateTime endsAt) {
		if (startsAt == null || endsAt == null) {
			return true;
		}
		Duration length = Duration.between(startsAt, endsAt);
		return !length.isNegative() && !length.isZero() && length.compareTo(MAX_LENGTH) <= 0;
	}
}
