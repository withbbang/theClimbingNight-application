package com.theclimbingnight.feed.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PostMediaSpec(
		@NotNull @Pattern(regexp = "image/jpeg", message = "image/jpeg만 올릴 수 있습니다.") String contentType,
		@Min(1) @Max(PostMediaSpec.MAX_BYTES) long byteSize,
		@Positive int width,
		@Positive int height) {

	public static final long MAX_BYTES = 15_728_640L;
}
