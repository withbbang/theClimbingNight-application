package com.theclimbingnight.meetup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record MeetupCopyRequest(@NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt) {

	@AssertTrue(message = "종료 시각은 시작 시각 뒤, 24시간 안이어야 합니다.")
	public boolean isValidSchedule() {
		return MeetupSchedule.isValid(startsAt, endsAt);
	}
}
