package com.theclimbingnight.roster.dto;

import java.util.List;

public record RosterImportResponse(int addedCount, List<SkippedRow> skipped, List<RowError> errors) {

	public record SkippedRow(long line, String somoimNickname, String reason) {
	}

	public record RowError(long line, String reason) {
	}

}
