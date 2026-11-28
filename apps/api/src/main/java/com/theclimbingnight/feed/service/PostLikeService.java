package com.theclimbingnight.feed.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.Cursor;
import com.theclimbingnight.feed.dto.LikeResponse;
import com.theclimbingnight.feed.dto.LikerResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostLike;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostLikeService {

	private final PostAccess access;
	private final PostLikeRepository likes;
	private final UserRepository users;
	private final UserAvatars avatars;
	private final Clock clock;

	public PostLikeService(PostAccess access, PostLikeRepository likes, UserRepository users, UserAvatars avatars,
			Clock clock) {
		this.access = access;
		this.likes = likes;
		this.users = users;
		this.avatars = avatars;
		this.clock = clock;
	}

	/**
	 * 게시물 행을 잠근 뒤 확인하고 넣으므로, 같은 글의 좋아요는 줄을 서서 처리되고 수가 어긋나지 않는다.
	 * post_likes.user_id 외래키가 사용자 행에 공유 잠금을 거므로, 탈퇴와 같은 순서(사용자 → 게시물)로 사용자 행을 먼저 잠근다.
	 */
	@Transactional
	public LikeResponse like(long userId, long postId) {
		users.findAllByIdForShare(List.of(userId)).stream().filter(User::isActive).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		Post post = access.lockWritable(userId, postId);
		if (!likes.existsByPostIdAndUserId(postId, userId)) {
			likes.save(PostLike.of(postId, userId, clock.instant()));
			post.addLike();
		}
		return new LikeResponse(post.getLikeCount(), true);
	}

	/** 숨겨진 글에서도 끌 수 있다. 지운 글만 404다. */
	@Transactional
	public LikeResponse unlike(long userId, long postId) {
		Post post = access.lockLive(postId);
		if (likes.deleteLike(postId, userId) == 1) {
			post.removeLike();
		}
		return new LikeResponse(post.getLikeCount(), false);
	}

	/** 읽을 수 있는 글만 본다. 차단 관계인 사람은 목록에서 빠진다. */
	public CursorResponse<LikerResponse> likers(long viewerId, long postId, String cursor, int size) {
		access.readable(viewerId, postId);
		Optional<Cursor> decoded = Cursor.decode(cursor);
		List<PostLike> found = likes.findLikers(postId, viewerId, decoded.map(Cursor::publishedAt).orElse(null),
				decoded.map(Cursor::id).orElse(null), PageRequest.of(0, size + 1));
		boolean hasNext = found.size() > size;
		List<PostLike> page = hasNext ? found.subList(0, size) : found;
		String nextCursor = hasNext ? Cursor.encode(page.get(size - 1).getCreatedAt(), page.get(size - 1).getUserId())
				: null;
		Map<Long, String> nicknames = new HashMap<>();
		Map<Long, ProfileImageUrls> images = Map.of();
		if (!page.isEmpty()) {
			List<Long> userIds = page.stream().map(PostLike::getUserId).toList();
			for (User user : users.findAllById(userIds)) {
				nicknames.put(user.getId(), user.getNickname());
			}
			images = avatars.of(userIds);
		}
		Map<Long, ProfileImageUrls> likerImages = images;
		return new CursorResponse<>(page.stream().map(l -> new LikerResponse(l.getUserId(),
				nicknames.getOrDefault(l.getUserId(), ""), UserAvatars.smallUrl(likerImages, l.getUserId()))).toList(),
				nextCursor);
	}
}
