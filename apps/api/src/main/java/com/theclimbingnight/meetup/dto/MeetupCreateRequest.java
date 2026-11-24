package com.theclimbingnight.meetup.dto;

import com.theclimbingnight.meetup.entity.MeetupSpec;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record MeetupCreateRequest(
		@NotBlank @Size(max = 100) String title,
		@Size(max = 5000) String description,
		@NotBlank @Size(max = 100) String placeName,
		@NotNull OffsetDateTime startsAt,
		@NotNull OffsetDateTime endsAt,
		@NotNull @Min(1) @Max(500) Integer capacity,
		@Min(0) @Max(168) Integer lateCancelHours,
		Boolean participantsVisible) {

	private static final int DEFAULT_LATE_CANCEL_HOURS = 24;

	@AssertTrue(message = "종료 시각은 시작 시각 뒤, 24시간 안이어야 합니다.")
	public boolean isValidSchedule() {
		return MeetupSchedule.isValid(startsAt, endsAt);
	}

	public MeetupSpec toSpec() {
		return new MeetupSpec(title, description, placeName, startsAt.toInstant(), endsAt.toInstant(), capacity,
				lateCancelHours == null ? DEFAULT_LATE_CANCEL_HOURS : lateCancelHours,
				participantsVisible == null || participantsVisible);
	}
}
