package com.theclimbingnight.feed.dto;

import jakarta.validation.constraints.Size;

public record PostUpdateRequest(@Size(max = 2200) String body) {
}
