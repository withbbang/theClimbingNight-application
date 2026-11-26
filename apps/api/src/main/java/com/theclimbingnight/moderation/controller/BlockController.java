package com.theclimbingnight.moderation.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.moderation.dto.BlockCreateRequest;
import com.theclimbingnight.moderation.dto.BlockedUserResponse;
import com.theclimbingnight.moderation.service.BlockService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blocks")
public class BlockController {

	private final BlockService blockService;

	public BlockController(BlockService blockService) {
		this.blockService = blockService;
	}

	/** 이미 차단한 사용자여도 201이다. */
	@RequireRole(Role.USER)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<BlockedUserResponse> block(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody BlockCreateRequest request) {
		return ApiResponse.success(blockService.block(user.id(), request.userId()));
	}

	@RequireRole(Role.USER)
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unblock(@CurrentUser AuthenticatedUser user, @PathVariable long userId) {
		blockService.unblock(user.id(), userId);
	}

	@RequireRole(Role.USER)
	@GetMapping
	public ApiResponse<List<BlockedUserResponse>> list(@CurrentUser AuthenticatedUser user) {
		return ApiResponse.success(blockService.list(user.id()));
	}
}
