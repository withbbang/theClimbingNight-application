package com.theclimbingnight.comment.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.comment.dto.CommentCreateRequest;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.service.CommentCommandService;
import com.theclimbingnight.comment.service.CommentQueryService;
import com.theclimbingnight.common.ApiResponse;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

	private final CommentQueryService commentQueryService;
	private final CommentCommandService commentCommandService;

	public CommentController(CommentQueryService commentQueryService, CommentCommandService commentCommandService) {
		this.commentQueryService = commentQueryService;
		this.commentCommandService = commentCommandService;
	}

	/** parentId가 없으면 최상위 댓글, 있으면 그 댓글의 답글이다. */
	@RequireRole(Role.USER)
	@GetMapping("/posts/{postId}/comments")
	public ApiResponse<CursorResponse<CommentResponse>> list(@CurrentUser AuthenticatedUser user,
			@PathVariable long postId, @RequestParam(required = false) Long parentId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(commentQueryService.list(user.id(), user.role(), postId, parentId, cursor, size));
	}

	@RequireRole(Role.USER)
	@PostMapping("/posts/{postId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CommentResponse> create(@CurrentUser AuthenticatedUser user, @PathVariable long postId,
			@Valid @RequestBody CommentCreateRequest request) {
		return ApiResponse.success(commentCommandService.create(user.id(), postId, request));
	}

	@RequireRole(Role.USER)
	@DeleteMapping("/comments/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
		commentCommandService.delete(user.id(), user.role(), id);
	}
}
