package com.theclimbingnight.notice.dto;

import com.theclimbingnight.notice.entity.Notice;
import java.time.Instant;

/** 작성자는 공개하지 않는다. */
public record NoticeResponse(long id, String title, String body, boolean pinned, Instant publishedAt,
		Instant updatedAt) {

	public static NoticeResponse from(Notice notice) {
		return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getBody(), notice.isPinned(),
				notice.getPublishedAt(), notice.getUpdatedAt());
	}
}
