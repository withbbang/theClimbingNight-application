package com.theclimbingnight.moderation.dto;

import com.theclimbingnight.feed.dto.PostResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 열린 신고를 대상별로 묶은 운영진용 항목. post는 POST 대상이고 지워지지 않았을 때만, userNickname은 USER 대상일 때만 값이 있다.
 * quarantineUrls는 숨긴 글의 격리본 presigned GET(5분)이고, 그 밖에는 빈 목록이다.
 */
public record ReportTargetResponse(String targetType, long targetId, String targetStatus, int reportCount,
		Map<String, Integer> reasons, List<String> details, OffsetDateTime lastReportedAt, PostResponse post,
		List<String> quarantineUrls, String userNickname) {
}
