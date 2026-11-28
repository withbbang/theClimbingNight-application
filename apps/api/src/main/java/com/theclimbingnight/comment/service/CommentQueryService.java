package com.theclimbingnight.comment.service;

import com.theclimbingnight.comment.dto.CommentDisplay;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.repository.ReplyCount;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.Cursor;
import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.service.PostAccess;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글·답글 목록. 지움·숨김 자리는 작성자와 본문을 내리지 않는다. */
@Service
@Transactional(readOnly = true)
public class CommentQueryService {

	private final PostAccess access;
	private final CommentRepository comments;
	private final UserRepository users;
	private final UserBlockRepository blocks;
	private final UserAvatars avatars;

	public CommentQueryService(PostAccess access, CommentRepository comments, UserRepository users,
			UserBlockRepository blocks, UserAvatars avatars) {
		this.access = access;
		this.comments = comments;
		this.users = users;
		this.blocks = blocks;
		this.avatars = avatars;
	}

	/** parentId가 있으면 그 최상위 댓글의 답글을, 없으면 최상위 댓글을 준다. */
	public CursorResponse<CommentResponse> list(long viewerId, Role role, long postId, Long parentId, String cursor,
			int size) {
		Post post = access.readable(viewerId, postId);
		if (parentId != null) {
			Comment parent = comments.findById(parentId)
					.filter(c -> c.getPostId().equals(postId) && c.getParentId() == null)
					.orElseThrow(CommentQueryService::notFound);
			if (blocks.existsBetween(viewerId, parent.getAuthorId())) {
				throw notFound();
			}
		}
		Long cursorId = Cursor.decode(cursor).map(Cursor::id).orElse(null);
		PageRequest limit = PageRequest.of(0, size + 1);
		List<Comment> found = parentId == null ? comments.findTopLevel(postId, viewerId, cursorId, limit)
				: comments.findReplies(parentId, viewerId, cursorId, limit);
		boolean hasNext = found.size() > size;
		List<Comment> page = hasNext ? found.subList(0, size) : found;
		String nextCursor = hasNext ? Cursor.encode(page.get(size - 1).getCreatedAt(), page.get(size - 1).getId())
				: null;

		Map<Long, Long> replyCounts = new HashMap<>();
		Map<Long, User> authors = new HashMap<>();
		Map<Long, ProfileImageUrls> images = Map.of();
		if (!page.isEmpty()) {
			if (parentId == null) {
				for (ReplyCount rc : comments.countShownReplies(page.stream().map(Comment::getId).toList())) {
					replyCounts.put(rc.parentId(), rc.count());
				}
			}
			Set<Long> userIds = new HashSet<>();
			for (Comment c : page) {
				userIds.add(c.getAuthorId());
				if (c.getReplyToUserId() != null) {
					userIds.add(c.getReplyToUserId());
				}
			}
			for (User user : users.findAllById(userIds)) {
				authors.put(user.getId(), user);
			}
			images = avatars.of(userIds);
		}
		Map<Long, ProfileImageUrls> authorImages = images;
		return new CursorResponse<>(
				page.stream().map(c -> toResponse(c, viewerId, role, post, authors, authorImages, replyCounts)).toList(),
				nextCursor);
	}

	private CommentResponse toResponse(Comment c, long viewerId, Role role, Post post, Map<Long, User> users,
			Map<Long, ProfileImageUrls> images, Map<Long, Long> replyCounts) {
		boolean mine = c.getAuthorId().equals(viewerId);
		CommentDisplay display;
		if (c.isShown()) {
			display = CommentDisplay.VISIBLE;
		} else if (mine && c.getDeletedAt() == null && c.isHidden()) {
			display = CommentDisplay.HIDDEN_MINE;
		} else if (c.getDeletedAt() != null) {
			display = CommentDisplay.DELETED;
		} else {
			display = CommentDisplay.HIDDEN;
		}
		int replyCount = replyCounts.getOrDefault(c.getId(), 0L).intValue();
		java.time.OffsetDateTime createdAt = c.getCreatedAt().atOffset(ZoneOffset.UTC);
		if (display == CommentDisplay.DELETED || display == CommentDisplay.HIDDEN) {
			return new CommentResponse(c.getId(), c.getParentId(), null, null, null, createdAt, display, replyCount,
					false, false);
		}
		User author = users.get(c.getAuthorId());
		String replyToNickname = Optional.ofNullable(c.getReplyToUserId()).map(users::get).filter(User::isActive)
				.map(User::getNickname).orElse(null);
		boolean deletable = mine || post.getAuthorId().equals(viewerId) || role == Role.ADMIN;
		return new CommentResponse(c.getId(), c.getParentId(),
				new PostAuthorResponse(c.getAuthorId(), author == null ? "" : author.getNickname(),
						UserAvatars.smallUrl(images, c.getAuthorId())),
				c.getBody(),
				replyToNickname, createdAt, display, replyCount, mine, deletable);
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.COMMON_NOT_FOUND);
	}
}
