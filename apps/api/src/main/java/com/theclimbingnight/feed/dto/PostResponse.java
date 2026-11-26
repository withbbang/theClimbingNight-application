package com.theclimbingnight.feed.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** status는 작성자 본인에게만 값이 있고 남에게는 null이다. */
public record PostResponse(long id, PostAuthorResponse author, String body, List<PostMediaResponse> media,
		int likeCount, int commentCount, OffsetDateTime publishedAt, String status, boolean mine) {
}
