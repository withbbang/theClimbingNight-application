package com.theclimbingnight.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SignupRequest(@NotBlank String signupToken, @NotBlank String nickname) {
}
