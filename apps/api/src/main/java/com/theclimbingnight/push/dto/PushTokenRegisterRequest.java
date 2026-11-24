package com.theclimbingnight.push.dto;

import com.theclimbingnight.push.entity.PushPlatform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PushTokenRegisterRequest(
		@NotNull @Pattern(regexp = "Expo(nent)?PushToken\\[[A-Za-z0-9_-]{1,200}\\]") String expoPushToken,
		@NotNull PushPlatform platform) {
}
