package com.theclimbingnight.moderation.service;

import com.theclimbingnight.moderation.entity.ReportTargetType;

/** 대상의 첫 열린 신고가 들어왔다. reporterId는 운영진 알림에서 신고자 본인을 빼려고 싣는다. */
public record ReportSubmittedEvent(ReportTargetType targetType, long targetId, long reporterId) {
}
