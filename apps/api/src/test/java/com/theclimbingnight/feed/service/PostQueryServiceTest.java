package com.theclimbingnight.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.Cursor;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

	private static final Instant T = Instant.parse("2026-11-26T12:00:00Z");
	private static final long ME = 1L;
	private static final long OTHER = 2L;

	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;
	@Mock UserRepository users;
	@Mock UserBlockRepository blocks;
	@Mock PostLikeRepository likes;

	private PostQueryService service;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		service = new PostQueryService(posts, mediaRepository, users, likes, new PostAccess(posts, blocks),
				new PostResponseMapper(urls));
	}

	private Post post(long id, long authorId, PostStatus status) {
		Post post = Post.draft(authorId);
		ReflectionTestUtils.setField(post, "id", id);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "publishedAt", T.minusSeconds(id));
		return post;
	}

	private PostMedia media(long id, long postId, PostMediaStatus status) {
		PostMedia media = PostMedia.image(postId, 0, "uploads/u" + id + ".jpg", "p/k" + id);
		ReflectionTestUtils.setField(media, "id", id);
		ReflectionTestUtils.setField(media, "status", status);
		return media;
	}

	private User user(long id, String nickname) {
		User user = User.create(nickname);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void 내가_누른_글은_likedByMe가_true다() {
		// given
		given(posts.findById(5L)).willReturn(Optional.of(post(5, OTHER, PostStatus.VISIBLE)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(false);
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection())).willReturn(List.of());
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));
		given(likes.findLikedPostIds(eq(ME), anyCollection())).willReturn(List.of(5L));

		// when / then
		assertThat(service.detail(ME, 5L).likedByMe()).isTrue();
	}

	@Test
	void 비로그인은_likedByMe가_false다() {
		// given
		given(posts.findLatestVisibleIds(3)).willReturn(List.of(8L));
		given(posts.findById(8L)).willReturn(Optional.of(post(8, OTHER, PostStatus.VISIBLE)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection())).willReturn(List.of());
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		// when / then
		assertThat(service.detail(null, 8L).likedByMe()).isFalse();
		verify(likes, never()).findLikedPostIds(anyLong(), anyCollection());
	}

	@Test
	void 차단_관계의_글_상세는_404다() {
		// given
		given(posts.findById(5L)).willReturn(Optional.of(post(5, OTHER, PostStatus.VISIBLE)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(true);

		// when / then
		assertThatThrownBy(() -> service.detail(ME, 5L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 남의_PENDING_글_상세는_404다() {
		// given
		given(posts.findById(5L)).willReturn(Optional.of(post(5, OTHER, PostStatus.PENDING)));

		// when / then
		assertThatThrownBy(() -> service.detail(ME, 5L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 내_숨겨진_글은_사진_URL_없이_상태와_함께_보인다() {
		// given
		given(posts.findById(5L)).willReturn(Optional.of(post(5, ME, PostStatus.HIDDEN)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection()))
				.willReturn(List.of(media(9, 5, PostMediaStatus.READY)));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(ME, "나")));

		// when
		PostResponse response = service.detail(ME, 5L);

		// then
		assertThat(response.status()).isEqualTo("HIDDEN");
		assertThat(response.mine()).isTrue();
		assertThat(response.media()).hasSize(1);
		assertThat(response.media().get(0).url()).isNull();
		assertThat(response.media().get(0).thumbnailUrl()).isNull();
	}

	@Test
	void 사진_URL은_공개_기본_주소와_키로_만든다() {
		// given
		given(posts.findById(5L)).willReturn(Optional.of(post(5, OTHER, PostStatus.VISIBLE)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(false);
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection()))
				.willReturn(List.of(media(9, 5, PostMediaStatus.READY)));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		// when
		PostResponse response = service.detail(ME, 5L);

		// then
		assertThat(response.status()).isNull();
		assertThat(response.author().nickname()).isEqualTo("남");
		assertThat(response.media().get(0).url()).isEqualTo("https://m.example/p/k9/d.webp");
		assertThat(response.media().get(0).thumbnailUrl()).isEqualTo("https://m.example/p/k9/t.webp");
	}

	@Test
	void 비로그인은_최신_3개_밖의_상세를_못_본다() {
		// given
		given(posts.findLatestVisibleIds(3)).willReturn(List.of(9L, 8L, 7L));

		// when / then
		assertThatThrownBy(() -> service.detail(null, 5L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(posts, never()).findById(anyLong());
	}

	@Test
	void 비로그인이_없는_글_상세를_부르면_401이다() {
		// given
		given(posts.findLatestVisibleIds(3)).willReturn(List.of(9L, 8L, 7L));

		// when / then
		assertThatThrownBy(() -> service.detail(null, 12345L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(posts, never()).findById(anyLong());
	}

	@Test
	void 비로그인이_최신_3개_안의_글_상세는_본다() {
		// given
		given(posts.findLatestVisibleIds(3)).willReturn(List.of(9L, 8L, 7L));
		given(posts.findById(8L)).willReturn(Optional.of(post(8, OTHER, PostStatus.VISIBLE)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection()))
				.willReturn(List.of(media(9, 8, PostMediaStatus.READY)));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		// when
		PostResponse response = service.detail(null, 8L);

		// then
		assertThat(response.id()).isEqualTo(8L);
		assertThat(response.mine()).isFalse();
		assertThat(response.status()).isNull();
		assertThat(response.media().get(0).url()).isEqualTo("https://m.example/p/k9/d.webp");
	}

	@Test
	void 피드_한_페이지의_사진과_작성자는_IN_조회_한_번씩이다() {
		// given
		given(posts.findFeed(anyLong(), isNull(), isNull(), any(Pageable.class))).willReturn(List.of(
				post(3, OTHER, PostStatus.VISIBLE), post(2, OTHER, PostStatus.VISIBLE), post(1, ME, PostStatus.VISIBLE)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection()))
				.willReturn(List.of(media(30, 3, PostMediaStatus.READY), media(20, 2, PostMediaStatus.READY)));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(ME, "나"), user(OTHER, "남")));

		// when
		CursorResponse<PostResponse> page = service.feed(ME, null, 2);

		// then
		assertThat(page.items()).extracting(PostResponse::id).containsExactly(3L, 2L);
		assertThat(page.nextCursor()).isEqualTo(Cursor.encode(T.minusSeconds(2), 2L));
		assertThat(page.items().get(0).media()).hasSize(1);
		verify(mediaRepository, times(1)).findAllByPostIdInOrderBySortOrder(anyCollection());
		verify(users, times(1)).findAllById(anyCollection());
	}

	@Test
	void 마지막_페이지는_nextCursor가_없다() {
		// given
		given(posts.findFeed(anyLong(), isNull(), isNull(), any(Pageable.class)))
				.willReturn(List.of(post(1, ME, PostStatus.VISIBLE)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection())).willReturn(List.of());
		given(users.findAllById(anyCollection())).willReturn(List.of(user(ME, "나")));

		// when
		CursorResponse<PostResponse> page = service.feed(ME, null, 2);

		// then
		assertThat(page.items()).hasSize(1);
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void 비로그인_피드는_최신_3개를_순서대로_주고_커서가_없다() {
		// given
		given(posts.findLatestVisibleIds(3)).willReturn(List.of(3L, 2L, 1L));
		given(posts.findAllById(anyCollection())).willReturn(List.of(
				post(1, OTHER, PostStatus.VISIBLE), post(2, OTHER, PostStatus.VISIBLE), post(3, OTHER, PostStatus.VISIBLE)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection())).willReturn(List.of());
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		// when
		CursorResponse<PostResponse> page = service.feed(null, null, 10);

		// then
		assertThat(page.items()).extracting(PostResponse::id).containsExactly(3L, 2L, 1L);
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void 비로그인이_커서를_보내면_AUTH_REQUIRED다() {
		assertThatThrownBy(() -> service.feed(null, Cursor.encode(T, 1L), 10))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
	}
}
