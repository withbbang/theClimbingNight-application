package com.theclimbingnight.membership.controller;

import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.membership.dto.MemberResponse;
import com.theclimbingnight.membership.service.MemberService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@RequireRole(Role.ADMIN)
	@GetMapping
	public ApiResponse<PageResponse<MemberResponse>> list(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(memberService.list(q, page, size));
	}

	@RequireRole(Role.ADMIN)
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@PathVariable long userId) {
		memberService.revoke(userId);
	}
}
