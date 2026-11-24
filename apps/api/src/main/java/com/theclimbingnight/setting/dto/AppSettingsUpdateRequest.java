package com.theclimbingnight.setting.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 전체 교체. null이면 그 설정을 지운다. */
public record AppSettingsUpdateRequest(
		@Size(max = 500) @Pattern(regexp = "https://\\S+") String somoimJoinUrl,
		@Size(max = 500) @Pattern(regexp = "https://\\S+") String smartstoreUrl) {
}
