package com.theclimbingnight.feed.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.PublicApi;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.feed.dto.PostCreateRequest;
import com.theclimbingnight.feed.dto.PostDraftResponse;
import com.theclimbingnight.feed.dto.PostPublishRequest;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.dto.PostUpdateRequest;
import com.theclimbingnight.feed.service.PostCommandService;
import com.theclimbingnight.feed.service.PostQueryService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

	private final PostCommandService postCommandService;

	private final PostQueryService postQueryService;

	public PostController(PostCommandService postCommandService, PostQueryService postQueryService) {
		this.postCommandService = postCommandService;
		this.postQueryService = postQueryService;
	}

	@PublicApi
	@GetMapping
	public ApiResponse<CursorResponse<PostResponse>> feed(@CurrentUser AuthenticatedUser user,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {
		return ApiResponse.success(postQueryService.feed(user == null ? null : user.id(), cursor, size));
	}

	@RequireRole(Role.USER)
	@GetMapping("/mine/in-progress")
	public ApiResponse<List<PostResponse>> inProgress(@CurrentUser AuthenticatedUser user) {
		return ApiResponse.success(postQueryService.inProgress(user.id()));
	}

	@PublicApi
	@GetMapping("/{id}")
	public ApiResponse<PostResponse> detail(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		return ApiResponse.success(postQueryService.detail(user == null ? null : user.id(), id));
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
