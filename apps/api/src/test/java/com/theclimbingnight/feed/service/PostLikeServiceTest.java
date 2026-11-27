package com.theclimbingnight.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.dto.LikeResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostLike;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
	private static final long ME = 1L;
	private static final long AUTHOR = 2L;
	private static final long POST = 5L;

	@Mock PostRepository posts;
	@Mock UserBlockRepository blocks;
	@Mock PostLikeRepository likes;
	@Mock UserRepository users;

	private PostLikeService service;

	@BeforeEach
	void setUp() {
		service = new PostLikeService(new PostAccess(posts, blocks), likes, users, Clock.fixed(NOW, ZoneOffset.UTC));
		lenient().when(users.findAllByIdForShare(anyCollection())).thenAnswer(inv -> {
			Collection<Long> ids = inv.getArgument(0);
			return ids.stream().map(PostLikeServiceTest::user).toList();
		});
	}

	private static User user(long id) {
		User user = User.create("u" + id);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Post post(PostStatus status, int likeCount) {
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST);
		ReflectionTestUtils.setField(post, "status", status);
		post.updateLikeCount(likeCount);
		return post;
	}

	private void givenPost(Post post) {
		given(posts.findByIdForUpdate(POST)).willReturn(Optional.of(post));
	}

	@Test
	void 처음_누르면_저장하고_수가_1_는다() {
		// given
		givenPost(post(PostStatus.VISIBLE, 3));
		given(likes.existsByPostIdAndUserId(POST, ME)).willReturn(false);

		// when
		LikeResponse response = service.like(ME, POST);

		// then
		assertThat(response).isEqualTo(new LikeResponse(4, true));
		verify(likes).save(any(PostLike.class));
	}

	@Test
	void 사용자_행을_공유_잠금한_뒤_게시물_행을_잠근다() {
		// given
		givenPost(post(PostStatus.VISIBLE, 3));
		given(likes.existsByPostIdAndUserId(POST, ME)).willReturn(false);

		// when
		service.like(ME, POST);

		// then
		InOrder order = inOrder(users, posts, likes);
		order.verify(users).findAllByIdForShare(List.of(ME));
		order.verify(posts).findByIdForUpdate(POST);
		order.verify(likes).save(any(PostLike.class));
	}

	@Test
	void 그새_탈퇴한_사용자는_누를_수_없다() {
		// given
		User withdrawn = user(ME);
		withdrawn.withdraw(NOW);
		given(users.findAllByIdForShare(List.of(ME))).willReturn(List.of(withdrawn));

		// when / then
		assertThatThrownBy(() -> service.like(ME, POST)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(posts, never()).findByIdForUpdate(POST);
	}

	@Test
	void 이미_누른_글을_다시_눌러도_수는_그대로다() {
		// given
		givenPost(post(PostStatus.VISIBLE, 3));
		given(likes.existsByPostIdAndUserId(POST, ME)).willReturn(true);

		// when
		LikeResponse response = service.like(ME, POST);

		// then
		assertThat(response).isEqualTo(new LikeResponse(3, true));
		verify(likes, never()).save(any(PostLike.class));
	}

	@Test
	void 누르지_않은_글을_취소해도_수는_그대로다() {
		// given
		givenPost(post(PostStatus.VISIBLE, 3));
		given(likes.deleteLike(POST, ME)).willReturn(0);

		// when
		LikeResponse response = service.unlike(ME, POST);

		// then
		assertThat(response).isEqualTo(new LikeResponse(3, false));
	}

	@Test
	void 누른_글을_취소하면_수가_1_준다() {
		// given
		givenPost(post(PostStatus.VISIBLE, 3));
		given(likes.deleteLike(POST, ME)).willReturn(1);

		// when / then
		assertThat(service.unlike(ME, POST)).isEqualTo(new LikeResponse(2, false));
	}

	@Test
	void 보이지_않는_글에는_누를_수_없다() {
		for (PostStatus status : new PostStatus[] {PostStatus.PENDING, PostStatus.HIDDEN}) {
			givenPost(post(status, 0));
			assertThatThrownBy(() -> service.like(ME, POST)).isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		}
		verify(likes, never()).save(any(PostLike.class));
	}

	@Test
	void 차단_관계인_사람_글에는_누를_수_없다() {
		// given
		givenPost(post(PostStatus.VISIBLE, 0));
		given(blocks.existsBetween(ME, AUTHOR)).willReturn(true);

		// when / then
		assertThatThrownBy(() -> service.like(ME, POST)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 숨겨진_글에서도_좋아요는_끌_수_있다() {
		// given
		givenPost(post(PostStatus.HIDDEN, 3));
		given(likes.deleteLike(POST, ME)).willReturn(1);

		// when / then
		assertThat(service.unlike(ME, POST)).isEqualTo(new LikeResponse(2, false));
	}

	@Test
	void 지운_글은_끌_수_없다() {
		// given
		Post post = post(PostStatus.VISIBLE, 3);
		post.softDelete(NOW);
		givenPost(post);

		// when / then
		assertThatThrownBy(() -> service.unlike(ME, POST)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}
}
