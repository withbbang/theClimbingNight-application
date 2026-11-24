package com.theclimbingnight.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 작성·수정 공용. */
public record NoticeRequest(@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 10000) String body,
		boolean pinned) {
}
