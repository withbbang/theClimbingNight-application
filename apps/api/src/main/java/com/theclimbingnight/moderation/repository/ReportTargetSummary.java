package com.theclimbingnight.moderation.repository;

import com.theclimbingnight.moderation.entity.ReportTargetType;
import java.time.Instant;

/** 열린 신고를 대상별로 묶은 한 줄. */
public record ReportTargetSummary(ReportTargetType targetType, Long targetId, Long reportCount, Instant lastReportedAt) {
}
