package com.theclimbingnight.auth.dto;

import com.theclimbingnight.auth.social.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(@NotNull SocialProvider provider, @NotBlank String token) {
}
