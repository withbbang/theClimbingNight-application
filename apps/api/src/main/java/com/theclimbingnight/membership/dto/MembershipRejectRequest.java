package com.theclimbingnight.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MembershipRejectRequest(@NotBlank @Size(max = 200) String reason) {
}
