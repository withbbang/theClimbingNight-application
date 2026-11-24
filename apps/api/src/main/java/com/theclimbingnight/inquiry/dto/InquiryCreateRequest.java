package com.theclimbingnight.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryCreateRequest(@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 2000) String body) {
}
