package com.theclimbingnight.user.dto;

import jakarta.validation.constraints.NotBlank;

public record NicknameUpdateRequest(@NotBlank String nickname) {
}
