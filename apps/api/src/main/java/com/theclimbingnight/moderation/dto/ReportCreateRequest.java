package com.theclimbingnight.moderation.dto;

import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(@NotNull ReportTargetType targetType, long targetId, @NotNull ReportReason reason,
		@Size(max = 500) String detail) {

	@AssertTrue(message = "댓글은 초상권 사유로 신고할 수 없습니다.")
	public boolean isReasonAllowedForTarget() {
		return !(targetType == ReportTargetType.COMMENT && reason == ReportReason.PORTRAIT_PRIVACY);
	}

	@AssertTrue(message = "기타 사유는 내용을 적어 주세요.")
	public boolean isDetailGivenForOther() {
		return reason != ReportReason.OTHER || (detail != null && !detail.isBlank());
	}
}
