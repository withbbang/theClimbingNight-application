package com.theclimbingnight.profile.dto;

/** bio가 null이거나 공백이면 자기소개를 지운다. */
public record BioUpdateRequest(String bio) {
}
