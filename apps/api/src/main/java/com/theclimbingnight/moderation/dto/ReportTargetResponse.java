package com.theclimbingnight.moderation.dto;

import com.theclimbingnight.feed.dto.PostResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 열린 신고를 대상별로 묶은 운영진용 항목. post는 POST 대상이거나 COMMENT 대상의 원 게시물이고 지워지지 않았을 때만, comment는 COMMENT 대상이고 댓글과 원 게시물이 지워지지 않았을 때만, userNickname·userProfileImageUrl(96px)·userBio는 USER 대상일 때만 값이 있다(사진·자기소개가 없으면 null).
 * quarantineUrls는 숨긴 글의 격리본 presigned GET(5분)이고, 그 밖에는 빈 목록이다.
 * cleanupFailed는 targetStatus와 별개로, 그 글 사진의 격리(QUARANTINE)나 삭제(DELETE_MEDIA) 작업이 세 번 실패해
 * 공개 사본이 남아 있을 수 있다는 뜻이다. USER 대상은 항상 false.
 */
public record ReportTargetResponse(String targetType, long targetId, String targetStatus, int reportCount,
		Map<String, Integer> reasons, List<String> details, OffsetDateTime lastReportedAt, PostResponse post,
		List<String> quarantineUrls, ReportCommentResponse comment, String userNickname, String userProfileImageUrl, String userBio,
		boolean cleanupFailed) {
}
