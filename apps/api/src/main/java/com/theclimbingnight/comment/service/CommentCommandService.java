package com.theclimbingnight.comment.service;

import com.theclimbingnight.comment.dto.CommentCreateRequest;
import com.theclimbingnight.comment.dto.CommentDisplay;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.service.PostAccess;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잠금 순서는 게시물 행 다음 댓글 행이다. 작성은 그 앞에 외래키로 참조할 사용자 행을 id 오름차순 공유 잠금해
 * 탈퇴(사용자 → 게시물)와 순서를 맞춘다. 댓글이 바뀔 때마다 게시물의 수를 다시 센다.
 */
@Service
@Transactional(readOnly = true)
public class CommentCommandService {

	private final PostAccess access;
	private final CommentRepository comments;
	private final UserRepository users;
	private final CommentCounter counter;
	private final UserBlockRepository blocks;
	private final UserAvatars avatars;
	private final Clock clock;

	public CommentCommandService(PostAccess access, CommentRepository comments, UserRepository users,
			CommentCounter counter, UserBlockRepository blocks, UserAvatars avatars, Clock clock) {
		this.access = access;
		this.comments = comments;
		this.users = users;
		this.counter = counter;
		this.blocks = blocks;
		this.avatars = avatars;
		this.clock = clock;
	}

	@Transactional
	public CommentResponse create(long userId, long postId, CommentCreateRequest request) {
		// 답글 대상 작성자는 엔티티를 올리지 않는 조회로 먼저 알아낸다. 댓글 엔티티는 게시물을 잠근 뒤 처음 읽는다.
		SortedSet<Long> referenced = new TreeSet<>();
		referenced.add(userId);
		if (request.replyToCommentId() != null) {
			referenced.add(comments.findAuthorIdById(request.replyToCommentId())
					.orElseThrow(CommentCommandService::notFound));
		}
		users.findAllByIdForShare(referenced).stream().filter(u -> u.getId().equals(userId) && u.isActive())
				.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		Post post = access.lockWritable(userId, postId);
		Long parentId = null;
		Long replyToUserId = null;
		if (request.replyToCommentId() != null) {
			Comment target = comments.findById(request.replyToCommentId())
					.filter(c -> c.getPostId().equals(postId) && c.isShown()).orElseThrow(CommentCommandService::notFound);
			if (blocks.existsBetween(userId, target.getAuthorId())) {
				throw notFound();
			}
			parentId = target.getParentId() != null ? target.getParentId() : target.getId();
			// 답글에 답하면 실제 부모(최상위 댓글) 작성자와의 차단도 본다. 부모 작성자는 INSERT가 참조하지 않아 잠그지 않는다.
			if (target.getParentId() != null && comments.findAuthorIdById(parentId)
					.map(parentAuthorId -> blocks.existsBetween(userId, parentAuthorId)).orElse(false)) {
				throw notFound();
			}
			replyToUserId = target.getAuthorId();
		}
		Comment saved = comments.save(Comment.create(postId, userId, parentId, replyToUserId, request.body().strip()));
		counter.recount(post);
		String nickname = users.findById(userId).map(User::getNickname).orElse(null);
		String replyToNickname = replyToUserId == null ? null
				: users.findById(replyToUserId).map(User::getNickname).orElse(null);
		return new CommentResponse(saved.getId(), parentId, new PostAuthorResponse(userId, nickname,
				UserAvatars.smallUrl(avatars.of(List.of(userId)), userId)),
				saved.getBody(), replyToNickname, saved.getCreatedAt().atOffset(ZoneOffset.UTC), CommentDisplay.VISIBLE, 0, true, true);
	}

	@Transactional
	public void delete(long userId, Role role, long commentId) {
		long postId = comments.findLivePostIdById(commentId).orElseThrow(CommentCommandService::notFound);
		Post post = access.lockLive(postId);
		Comment comment = comments.findByIdForUpdate(commentId).filter(c -> c.getDeletedAt() == null)
				.orElseThrow(CommentCommandService::notFound);
		boolean allowed = comment.getAuthorId().equals(userId) || post.getAuthorId().equals(userId)
				|| role == Role.ADMIN;
		if (!allowed) {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}
		comment.softDelete(clock.instant());
		counter.recount(post);
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.COMMON_NOT_FOUND);
	}
}
