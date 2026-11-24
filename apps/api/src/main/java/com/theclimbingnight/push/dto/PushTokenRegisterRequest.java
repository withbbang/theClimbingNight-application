package com.theclimbingnight.push.dto;

import com.theclimbingnight.push.entity.PushPlatform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PushTokenRegisterRequest(
		@NotNull @Pattern(regexp = PushTokenRegisterRequest.EXPO_PUSH_TOKEN_PATTERN) String expoPushToken,
		@NotNull PushPlatform platform) {

	/** 등록과 로그아웃 삭제가 같은 형식 기준을 쓴다. ASCII만 허용해 ascii 컬럼 비교가 실패하지 않게 한다. */
	public static final String EXPO_PUSH_TOKEN_PATTERN = "Expo(nent)?PushToken\\[[A-Za-z0-9_-]{1,200}\\]";
}
