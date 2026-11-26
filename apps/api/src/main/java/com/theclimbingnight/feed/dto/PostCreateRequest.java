package com.theclimbingnight.feed.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostCreateRequest(@NotNull @Size(min = 1, max = 10) List<@Valid PostMediaSpec> media) {
}
