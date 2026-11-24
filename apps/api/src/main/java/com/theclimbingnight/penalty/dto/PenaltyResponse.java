package com.theclimbingnight.penalty.dto;

import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import java.time.Instant;

/** active는 취소(revoke)되지 않은 기록이면 true. revokeReason은 취소된 기록만 채운다. */
public record PenaltyResponse(long id, long meetupId, String meetupTitle, Instant meetupStartsAt, PenaltyType type,
		boolean active, String revokeReason) {

	public static PenaltyResponse from(Penalty penalty) {
		return new PenaltyResponse(penalty.getId(), penalty.getMeetup().getId(), penalty.getMeetup().getTitle(),
				penalty.getMeetup().getStartsAt(), penalty.getType(), penalty.isActive(), penalty.getRevokeReason());
	}
}
