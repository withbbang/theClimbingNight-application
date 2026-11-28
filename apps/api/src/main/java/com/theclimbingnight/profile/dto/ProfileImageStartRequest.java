package com.theclimbingnight.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProfileImageStartRequest(@Min(1) @Max(5_242_880) long byteSize) {
}
