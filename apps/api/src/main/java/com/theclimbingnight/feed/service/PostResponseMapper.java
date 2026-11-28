package com.theclimbingnight.feed.service;

import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.feed.dto.PostMediaResponse;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.media.MediaUrls;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

/** 사진 URL은 READY이고 글이 숨김 상태가 아닐 때만 준다. status는 작성자에게만 준다. */
@Component
public class PostResponseMapper {

	private final MediaUrls mediaUrls;

	public PostResponseMapper(MediaUrls mediaUrls) {
		this.mediaUrls = mediaUrls;
	}

	public PostResponse toResponse(Post post, String authorNickname, String authorImageUrl,
			List<PostMedia> media, Long viewerId,
			boolean likedByMe) {
		boolean mine = viewerId != null && post.getAuthorId().equals(viewerId);
		boolean hidden = post.getStatus() == PostStatus.HIDDEN || post.getStatus() == PostStatus.HIDDEN_PENDING;
		return new PostResponse(post.getId(), new PostAuthorResponse(post.getAuthorId(), authorNickname, authorImageUrl),
				post.getBody(), media.stream().map(m -> toMedia(m, hidden)).toList(), post.getLikeCount(),
				post.getCommentCount(), likedByMe,
				post.getPublishedAt() == null ? null : post.getPublishedAt().atOffset(ZoneOffset.UTC),
				mine ? post.getStatus().name() : null, mine);
	}

	private PostMediaResponse toMedia(PostMedia media, boolean hidden) {
		boolean visible = media.getStatus() == PostMediaStatus.READY && !hidden;
		return new PostMediaResponse(media.getId(), media.getType().name(),
				visible ? mediaUrls.publicUrl(media.displayKey()) : null,
				visible ? mediaUrls.publicUrl(media.thumbKey()) : null,
				media.getWidth() == null ? 0 : media.getWidth(), media.getHeight() == null ? 0 : media.getHeight());
	}
}
