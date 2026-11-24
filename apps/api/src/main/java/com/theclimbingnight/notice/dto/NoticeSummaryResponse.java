package com.theclimbingnight.notice.dto;

import com.theclimbingnight.notice.entity.Notice;
import java.time.Instant;

public record NoticeSummaryResponse(long id, String title, boolean pinned, Instant publishedAt) {

	public static NoticeSummaryResponse from(Notice notice) {
		return new NoticeSummaryResponse(notice.getId(), notice.getTitle(), notice.isPinned(), notice.getPublishedAt());
	}
}
