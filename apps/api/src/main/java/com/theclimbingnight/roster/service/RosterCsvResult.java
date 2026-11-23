package com.theclimbingnight.roster.service;

import com.theclimbingnight.roster.dto.RosterImportResponse;
import java.util.List;

public record RosterCsvResult(List<RosterCsvRow> rows, List<RosterImportResponse.RowError> errors) {
}
