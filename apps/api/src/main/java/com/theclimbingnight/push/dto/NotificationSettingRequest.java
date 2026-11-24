package com.theclimbingnight.push.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingRequest(@NotNull Boolean newMeetup, @NotNull Boolean newNotice) {
}
