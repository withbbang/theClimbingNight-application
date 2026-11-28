package com.theclimbingnight.moderation.dto;

import jakarta.validation.constraints.NotNull;

public record ReportTargetActionRequest(@NotNull Action action) {

	public enum Action {
		HIDE, RESTORE, DISMISS, REMOVE_PROFILE_IMAGE, CLEAR_BIO, REMOVE_PROFILE
	}
}
