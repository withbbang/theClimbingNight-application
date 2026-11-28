package com.theclimbingnight.profile.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.Cursor;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.profile.dto.GridPostResponse;
import com.theclimbingnight.profile.dto.ProfileResponse;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfileQueryService {

	private static final List<PostStatus> OTHERS_VIEW = List.of(PostStatus.VISIBLE);
	private static final List<PostStatus> OWNER_VIEW = List.of(PostStatus.VISIBLE, PostStatus.HIDDEN_PENDING,
			PostStatus.HIDDEN);

	private final UserRepository users;
	private final UserBlockRepository blocks;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final UserAvatars avatars;
	private final MediaUrls urls;

	public ProfileQueryService(UserRepository users, UserBlockRepository blocks, PostRepository posts,
			PostMediaRepository mediaRepository, UserAvatars avatars, MediaUrls urls) {
		this.users = users;
		this.blocks = blocks;
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.avatars = avatars;
		this.urls = urls;
	}

	public ProfileResponse profile(long viewerId, long userId) {
		User user = accessible(viewerId, userId);
		return new ProfileResponse(user.getId(), user.getNickname(), user.getBio(),
				avatars.of(List.of(userId)).get(userId),
				posts.countByAuthorIdAndStatusAndDeletedAtIsNull(userId, PostStatus.VISIBLE), viewerId == userId);
	}

	public CursorResponse<GridPostResponse> posts(long viewerId, long userId, String cursor, int size) {
		accessible(viewerId, userId);
		boolean mine = viewerId == userId;
		Optional<Cursor> decoded = Cursor.decode(cursor);
		List<Post> found = posts.findProfileGrid(userId, mine ? OWNER_VIEW : OTHERS_VIEW,
				decoded.map(Cursor::publishedAt).orElse(null), decoded.map(Cursor::id).orElse(null),
				PageRequest.of(0, size + 1));
		boolean hasNext = found.size() > size;
		List<Post> page = hasNext ? found.subList(0, size) : found;
		String nextCursor = hasNext ? Cursor.encode(page.get(size - 1).getPublishedAt(), page.get(size - 1).getId())
				: null;
		if (page.isEmpty()) {
			return new CursorResponse<>(List.of(), null);
		}
		Map<Long, List<PostMedia>> media = mediaRepository
				.findAllByPostIdInOrderBySortOrder(page.stream().map(Post::getId).toList()).stream()
				.collect(Collectors.groupingBy(PostMedia::getPostId));
		List<GridPostResponse> items = page.stream().map(post -> {
			List<PostMedia> files = media.getOrDefault(post.getId(), List.of());
			boolean visible = post.getStatus() == PostStatus.VISIBLE;
			String thumbnail = visible && !files.isEmpty() ? urls.publicUrl(files.get(0).thumbKey()) : null;
			String status = mine ? post.getStatus().name() : null;
			return new GridPostResponse(post.getId(), thumbnail, files.size(), status);
		}).toList();
		return new CursorResponse<>(items, nextCursor);
	}

	/** 없거나 탈퇴했거나 차단 관계면 404. 본인은 차단 검사를 건너뛴다. */
	private User accessible(long viewerId, long userId) {
		User user = users.findById(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (viewerId != userId && blocks.existsBetween(viewerId, userId)) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		return user;
	}
}
