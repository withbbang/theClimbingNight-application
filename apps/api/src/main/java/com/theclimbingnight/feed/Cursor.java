package com.theclimbingnight.feed;

import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** 피드 커서. base64url(`{게시 시각 epoch 마이크로초}:{id}`). 앱에는 불투명 문자열이다. */
public record Cursor(Instant publishedAt, long id) {

	public static String encode(Instant publishedAt, long id) {
		long micros = ChronoUnit.MICROS.between(Instant.EPOCH, publishedAt);
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString((micros + ":" + id).getBytes(StandardCharsets.UTF_8));
	}

	/** 비었으면 빈 값, 깨졌으면 COMMON_VALIDATION_FAILED(field cursor). */
	public static Optional<Cursor> decode(String value) {
		if (value == null || value.isEmpty()) {
			return Optional.empty();
		}
		try {
			String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split(":", -1);
			if (parts.length != 2) {
				throw invalid();
			}
			return Optional.of(new Cursor(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS),
					Long.parseLong(parts[1])));
		} catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED,
				List.of(new ApiResponse.FieldReason("cursor", "올바르지 않은 커서입니다.")));
	}
}
