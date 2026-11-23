package com.theclimbingnight.roster.controller;

import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.roster.dto.RosterImportResponse;
import com.theclimbingnight.roster.service.RosterImportService;
import com.theclimbingnight.user.entity.Role;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/roster-members")
public class RosterMemberController {

	private final RosterImportService rosterImportService;

	public RosterMemberController(RosterImportService rosterImportService) {
		this.rosterImportService = rosterImportService;
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

}
