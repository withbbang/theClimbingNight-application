package com.theclimbingnight.feed.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.Cursor;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostQueryService {

	/** 비로그인에게 보여주는 최신 글 수. */
	static final int ANONYMOUS_LIMIT = 3;
	private static final int IN_PROGRESS_LIMIT = 10;

	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final UserRepository users;
	private final PostLikeRepository likes;
	private final PostAccess access;
	private final PostResponseMapper mapper;

	public PostQueryService(PostRepository posts, PostMediaRepository mediaRepository, UserRepository users,
			PostLikeRepository likes, PostAccess access, PostResponseMapper mapper) {
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.users = users;
		this.likes = likes;
		this.access = access;
		this.mapper = mapper;
	}

	/** viewerId가 null이면 비로그인이다. */
	public CursorResponse<PostResponse> feed(Long viewerId, String cursor, int size) {
		if (viewerId == null) {
			if (cursor != null && !cursor.isEmpty()) {
				throw new BusinessException(ErrorCode.AUTH_REQUIRED);
			}
			return new CursorResponse<>(anonymousFeed(), null);
		}
		Optional<Cursor> decoded = Cursor.decode(cursor);
		List<Post> found = posts.findFeed(viewerId, decoded.map(Cursor::publishedAt).orElse(null),
				decoded.map(Cursor::id).orElse(null), PageRequest.of(0, size + 1));
		boolean hasNext = found.size() > size;
		List<Post> page = hasNext ? found.subList(0, size) : found;
		String nextCursor = hasNext ? Cursor.encode(page.get(size - 1).getPublishedAt(), page.get(size - 1).getId())
				: null;
		return new CursorResponse<>(toResponses(page, viewerId), nextCursor);
	}

	public PostResponse detail(Long viewerId, long id) {
		if (viewerId == null) {
			// 존재 여부를 흘리지 않도록 최신 3개 밖이면(없는 글·삭제 글 포함) 항상 401이다.
			if (!posts.findLatestVisibleIds(ANONYMOUS_LIMIT).contains(id)) {
				throw new BusinessException(ErrorCode.AUTH_REQUIRED);
			}
		}
		if (viewerId == null) {
			Post post = posts.findById(id).filter(p -> p.getDeletedAt() == null)
					.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
			return toResponses(List.of(post), null).get(0);
		}
		return toResponses(List.of(access.readable(viewerId, id)), viewerId).get(0);
	}

	public List<PostResponse> inProgress(long userId) {
		return toResponses(posts.findTop10ByAuthorIdAndStatusInAndDeletedAtIsNullOrderByIdDesc(userId,
				List.of(PostStatus.PENDING, PostStatus.FAILED)), userId);
	}

	private List<PostResponse> anonymousFeed() {
		List<Long> ids = posts.findLatestVisibleIds(ANONYMOUS_LIMIT);
		Map<Long, Post> byId = posts.findAllById(ids).stream().collect(Collectors.toMap(Post::getId, p -> p));
		return toResponses(ids.stream().map(byId::get).filter(p -> p != null).toList(), null);
	}

	/** 한 페이지의 사진과 작성자를 IN 조회 두 번으로 모은다. */
	private List<PostResponse> toResponses(List<Post> page, Long viewerId) {
		if (page.isEmpty()) {
			return List.of();
		}
		Collection<Long> postIds = page.stream().map(Post::getId).toList();
		Map<Long, List<PostMedia>> media = mediaRepository.findAllByPostIdInOrderBySortOrder(postIds).stream()
				.collect(Collectors.groupingBy(PostMedia::getPostId));
		Map<Long, String> nicknames = new HashMap<>();
		for (User user : users.findAllById(page.stream().map(Post::getAuthorId).distinct().toList())) {
			nicknames.put(user.getId(), user.getNickname());
		}
		Set<Long> liked = viewerId == null ? Set.of() : new HashSet<>(likes.findLikedPostIds(viewerId, postIds));
		return page.stream().map(post -> mapper.toResponse(post, nicknames.getOrDefault(post.getAuthorId(), ""),
				media.getOrDefault(post.getId(), List.of()), viewerId, liked.contains(post.getId()))).toList();
	}
}
