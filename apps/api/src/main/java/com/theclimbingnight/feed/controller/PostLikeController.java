package com.theclimbingnight.feed.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.feed.dto.LikeResponse;
import com.theclimbingnight.feed.dto.LikerResponse;
import com.theclimbingnight.feed.service.PostLikeService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts/{id}")
public class PostLikeController {

	private final PostLikeService postLikeService;

	public PostLikeController(PostLikeService postLikeService) {
		this.postLikeService = postLikeService;
	}

	@RequireRole(Role.USER)
	@PutMapping("/like")
	public ApiResponse<LikeResponse> like(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(postLikeService.like(user.id(), id));
	}

	@RequireRole(Role.USER)
	@DeleteMapping("/like")
	public ApiResponse<LikeResponse> unlike(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(postLikeService.unlike(user.id(), id));
	}

	@RequireRole(Role.USER)
	@GetMapping("/likes")
	public ApiResponse<CursorResponse<LikerResponse>> likers(@CurrentUser AuthenticatedUser user,
			@PathVariable long id, @RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(postLikeService.likers(user.id(), id, cursor, size));
	}
}
