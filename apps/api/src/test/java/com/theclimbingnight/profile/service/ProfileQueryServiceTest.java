package com.theclimbingnight.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.Cursor;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.profile.dto.GridPostResponse;
import com.theclimbingnight.profile.dto.ProfileResponse;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Instant;
import java.util.ArrayList;
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
class ProfileQueryServiceTest {

	private static final Instant T = Instant.parse("2026-11-26T12:00:00Z");
	private static final long ME = 1L;
	private static final long OTHER = 2L;

	@Mock UserRepository users;
	@Mock UserBlockRepository blocks;
	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;
	@Mock ProfileImageRepository images;

	private ProfileQueryService service;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		service = new ProfileQueryService(users, blocks, posts, mediaRepository, new UserAvatars(images, urls), urls);
	}

	private User user(long id, boolean active) {
		User user = User.create("climber" + id);
		ReflectionTestUtils.setField(user, "id", id);
		if (!active) {
			user.withdraw(T);
		}
		return user;
	}

	private Post post(long id, long authorId, PostStatus status) {
		Post post = Post.draft(authorId);
		ReflectionTestUtils.setField(post, "id", id);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "publishedAt", T.minusSeconds(id));
		return post;
	}

	private PostMedia media(long id, long postId, int order) {
		PostMedia media = PostMedia.image(postId, order, "uploads/u" + id + ".jpg", "p/k" + id);
		ReflectionTestUtils.setField(media, "id", id);
		return media;
	}

	@Test
	void othersSeeOnlyVisible() {
		// given
		given(users.findById(OTHER)).willReturn(Optional.of(user(OTHER, true)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(false);
		given(posts.countByAuthorIdAndStatusAndDeletedAtIsNull(OTHER, PostStatus.VISIBLE)).willReturn(2L);
		given(posts.findProfileGrid(eq(OTHER), eq(List.of(PostStatus.VISIBLE)), isNull(), isNull(), any(Pageable.class)))
				.willReturn(List.of(post(1, OTHER, PostStatus.VISIBLE), post(2, OTHER, PostStatus.VISIBLE)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection())).willReturn(List.of());

		// when
		ProfileResponse profile = service.profile(ME, OTHER);
		CursorResponse<GridPostResponse> grid = service.posts(ME, OTHER, null, 18);

		// then
		assertThat(profile.postCount()).isEqualTo(2);
		assertThat(profile.mine()).isFalse();
		assertThat(grid.items()).hasSize(2).allSatisfy(item -> assertThat(item.status()).isNull());
	}

	@Test
	void ownerSeesHiddenWithoutThumbnail() {
		// given
		given(users.findById(ME)).willReturn(Optional.of(user(ME, true)));
		given(posts.findProfileGrid(eq(ME),
				eq(List.of(PostStatus.VISIBLE, PostStatus.HIDDEN_PENDING, PostStatus.HIDDEN)), isNull(), isNull(),
				any(Pageable.class)))
				.willReturn(List.of(post(1, ME, PostStatus.VISIBLE), post(2, ME, PostStatus.HIDDEN),
						post(3, ME, PostStatus.HIDDEN_PENDING)));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection()))
				.willReturn(List.of(media(11, 1, 0), media(12, 2, 0), media(13, 3, 0)));
		given(posts.countByAuthorIdAndStatusAndDeletedAtIsNull(ME, PostStatus.VISIBLE)).willReturn(1L);

		// when
		List<GridPostResponse> items = service.posts(ME, ME, null, 18).items();

		// then
		assertThat(items).extracting(GridPostResponse::status).containsExactly("VISIBLE", "HIDDEN", "HIDDEN_PENDING");
		assertThat(items.get(0).thumbnailUrl()).isEqualTo("https://m.example/p/k11/t.webp");
		assertThat(items.get(1).thumbnailUrl()).isNull();
		assertThat(items.get(2).thumbnailUrl()).isNull();
		assertThat(service.profile(ME, ME).postCount()).isEqualTo(1);
		assertThat(service.profile(ME, ME).mine()).isTrue();
	}

	@Test
	void blockedByViewerIs404() {
		// given: 내가 차단한 사람 (existsBetween은 어느 방향이든 true)
		given(users.findById(OTHER)).willReturn(Optional.of(user(OTHER, true)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(true);

		// when / then
		assertThatThrownBy(() -> service.profile(ME, OTHER)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThatThrownBy(() -> service.posts(ME, OTHER, null, 18)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void blockingViewerIs404() {
		// given: 나를 차단한 사람. 서비스는 (viewer, target) 순서로만 묻는다.
		given(users.findById(OTHER)).willReturn(Optional.of(user(OTHER, true)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(true);

		// when / then
		assertThatThrownBy(() -> service.profile(ME, OTHER)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThatThrownBy(() -> service.posts(ME, OTHER, null, 18)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void withdrawnIs404() {
		// given
		given(users.findById(OTHER)).willReturn(Optional.of(user(OTHER, false)));
		given(users.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.profile(ME, OTHER)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThatThrownBy(() -> service.posts(ME, 99L, null, 18)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void gridPagesByCursor() {
		// given
		given(users.findById(OTHER)).willReturn(Optional.of(user(OTHER, true)));
		given(blocks.existsBetween(ME, OTHER)).willReturn(false);
		List<Post> all = new ArrayList<>();
		for (long i = 1; i <= 20; i++) {
			all.add(post(i, OTHER, PostStatus.VISIBLE));
		}
		given(posts.findProfileGrid(eq(OTHER), anyCollection(), isNull(), isNull(), any(Pageable.class)))
				.willReturn(all.subList(0, 19));
		Post last = all.get(17);
		given(posts.findProfileGrid(eq(OTHER), anyCollection(), eq(last.getPublishedAt()), eq(last.getId()),
				any(Pageable.class))).willReturn(all.subList(18, 20));
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(anyCollection())).willAnswer(inv -> {
			List<PostMedia> found = new ArrayList<>();
			for (Long postId : (java.util.Collection<Long>) inv.getArgument(0)) {
				found.add(media(100 + postId, postId, 0));
				found.add(media(200 + postId, postId, 1));
			}
			return found;
		});

		// when
		CursorResponse<GridPostResponse> first = service.posts(ME, OTHER, null, 18);
		CursorResponse<GridPostResponse> second = service.posts(ME, OTHER, first.nextCursor(), 18);

		// then
		assertThat(first.items()).hasSize(18);
		assertThat(first.nextCursor()).isEqualTo(Cursor.encode(last.getPublishedAt(), last.getId()));
		assertThat(first.items().get(0).thumbnailUrl()).isEqualTo("https://m.example/p/k101/t.webp");
		assertThat(first.items().get(0).mediaCount()).isEqualTo(2);
		assertThat(second.items()).hasSize(2);
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	void profileImageOnlyWhenReady() {
		// given
		User withPhoto = user(OTHER, true);
		given(users.findById(OTHER)).willReturn(Optional.of(withPhoto));
		given(users.findById(3L)).willReturn(Optional.of(user(3L, true)));
		given(blocks.existsBetween(anyLong(), anyLong())).willReturn(false);
		given(images.findCurrentReady(List.of(OTHER)))
				.willReturn(List.of(new ProfileImageRepository.CurrentImage(OTHER, "a/abc")));
		given(images.findCurrentReady(List.of(3L))).willReturn(List.of());

		// when
		ProfileResponse ready = service.profile(ME, OTHER);
		ProfileResponse none = service.profile(ME, 3L);

		// then
		assertThat(ready.profileImage().largeUrl()).isEqualTo("https://m.example/a/abc/l.webp");
		assertThat(ready.profileImage().smallUrl()).isEqualTo("https://m.example/a/abc/s.webp");
		assertThat(none.profileImage()).isNull();
	}
}
