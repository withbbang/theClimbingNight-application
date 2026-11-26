package com.theclimbingnight.moderation.dto;

import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(@NotNull ReportTargetType targetType, long targetId, @NotNull ReportReason reason,
		@Size(max = 500) String detail) {

	/** 댓글 신고는 댓글 기능과 함께 연다. */
	@AssertTrue(message = "지금은 게시물과 사용자만 신고할 수 있습니다.")
	public boolean isSupportedTarget() {
		return targetType != ReportTargetType.COMMENT;
	}

	@AssertTrue(message = "기타 사유는 내용을 적어 주세요.")
	public boolean isDetailGivenForOther() {
		return reason != ReportReason.OTHER || (detail != null && !detail.isBlank());
	}
}
