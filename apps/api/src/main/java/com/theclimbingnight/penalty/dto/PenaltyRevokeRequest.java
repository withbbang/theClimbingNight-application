package com.theclimbingnight.penalty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PenaltyRevokeRequest(@NotBlank @Size(max = 200) String reason) {
}
