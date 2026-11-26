package com.theclimbingnight.feed.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.feed.dto.PostCreateRequest;
import com.theclimbingnight.feed.dto.PostDraftResponse;
import com.theclimbingnight.feed.dto.PostPublishRequest;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.dto.PostUpdateRequest;
import com.theclimbingnight.feed.service.PostCommandService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

	private final PostCommandService postCommandService;

	public PostController(PostCommandService postCommandService) {
		this.postCommandService = postCommandService;
	}

	@RequireRole(Role.USER)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PostDraftResponse> create(@CurrentUser AuthenticatedUser user,
			@Valid @RequestBody PostCreateRequest request) {
		return ApiResponse.success(postCommandService.createDraft(user.id(), request));
	}

	@RequireRole(Role.USER)
	@PostMapping("/{id}/publish")
	public ApiResponse<PostResponse> publish(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody PostPublishRequest request) {
		return ApiResponse.success(postCommandService.publish(user.id(), id, request.body()));
	}

	@RequireRole(Role.USER)
	@PatchMapping("/{id}")
	public ApiResponse<PostResponse> update(@CurrentUser AuthenticatedUser user, @PathVariable long id,
			@Valid @RequestBody PostUpdateRequest request) {
		return ApiResponse.success(postCommandService.update(user.id(), id, request.body()));
	}

	@RequireRole(Role.USER)
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		postCommandService.delete(user.id(), user.role(), id);
	}
}
