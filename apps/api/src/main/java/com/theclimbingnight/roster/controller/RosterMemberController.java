package com.theclimbingnight.roster.controller;

import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.roster.dto.RosterImportResponse;
import com.theclimbingnight.roster.dto.RosterMemberResponse;
import com.theclimbingnight.roster.service.RosterImportService;
import com.theclimbingnight.roster.service.RosterQueryService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/roster-members")
public class RosterMemberController {

	private final RosterImportService rosterImportService;
	private final RosterQueryService rosterQueryService;

	public RosterMemberController(RosterImportService rosterImportService, RosterQueryService rosterQueryService) {
		this.rosterImportService = rosterImportService;
		this.rosterQueryService = rosterQueryService;
	}

	@RequireRole(Role.ADMIN)
	@PostMapping("/import")
	public ApiResponse<RosterImportResponse> importCsv(@RequestPart("file") MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			return ApiResponse.success(rosterImportService.importCsv(in));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@RequireRole(Role.ADMIN)
	@GetMapping
	public ApiResponse<PageResponse<RosterMemberResponse>> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) Boolean linked,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(rosterQueryService.search(q, linked, page, size));
	}

}
