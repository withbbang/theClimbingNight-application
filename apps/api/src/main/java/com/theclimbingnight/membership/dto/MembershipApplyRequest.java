package com.theclimbingnight.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MembershipApplyRequest(@NotBlank @Size(max = 50) String somoimNickname) {
}
