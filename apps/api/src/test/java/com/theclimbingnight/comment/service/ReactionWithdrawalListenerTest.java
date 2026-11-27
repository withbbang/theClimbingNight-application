package com.theclimbingnight.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class ReactionWithdrawalListenerTest {

	private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
	private static final long ME = 1L;

	@Mock PostRepository posts;
	@Mock PostLikeRepository likes;
	@Mock CommentRepository comments;
	@Mock CommentCounter counter;

	private ReactionWithdrawalListener listener;

	@BeforeEach
	void setUp() {
		listener = new ReactionWithdrawalListener(posts, likes, comments, counter, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Post post(long id) {
		Post post = Post.draft(99L);
		ReflectionTestUtils.setField(post, "id", id);
		return post;
	}

	@Test
	void 여러_게시물의_좋아요와_댓글을_지우고_두_수를_다시_센다() {
		// given
		Post p3 = post(3L);
		Post p7 = post(7L);
		Post deleted = post(9L);
		deleted.softDelete(NOW);
		given(likes.findPostIdsByUserId(ME)).willReturn(List.of(3L, 9L));
		given(comments.findLivePostIdsByAuthorId(ME)).willReturn(List.of(3L, 7L));
		given(posts.findByIdForUpdate(3L)).willReturn(Optional.of(p3));
		given(posts.findByIdForUpdate(7L)).willReturn(Optional.of(p7));
		given(posts.findByIdForUpdate(9L)).willReturn(Optional.of(deleted));
		given(likes.countByPostId(3L)).willReturn(4L);
		given(likes.countByPostId(7L)).willReturn(2L);

		// when
		listener.on(new UserWithdrawnEvent(ME));

		// then
		verify(likes).deleteLike(3L, ME);
		verify(likes).deleteLike(7L, ME);
		verify(likes).deleteLike(9L, ME);
		verify(comments).softDeleteByPostIdAndAuthorId(3L, ME, NOW);
		verify(comments).softDeleteByPostIdAndAuthorId(7L, ME, NOW);
		verify(comments).softDeleteByPostIdAndAuthorId(9L, ME, NOW);
		assertThat(p3.getLikeCount()).isEqualTo(4);
		assertThat(p7.getLikeCount()).isEqualTo(2);
		verify(counter).recount(p3);
		verify(counter).recount(p7);
		verify(counter, never()).recount(deleted);
		verify(likes, never()).countByPostId(9L);
	}

	@Test
	void 게시물은_id_오름차순으로_잠근다() {
		// given
		given(likes.findPostIdsByUserId(ME)).willReturn(List.of(8L, 2L, 5L));
		given(comments.findLivePostIdsByAuthorId(ME)).willReturn(List.of(5L, 1L));
		given(posts.findByIdForUpdate(anyLong())).willAnswer(i -> Optional.of(post(i.getArgument(0))));

		// when
		listener.on(new UserWithdrawnEvent(ME));

		// then
		InOrder order = inOrder(posts);
		for (long id : new long[] {1L, 2L, 5L, 8L}) {
			order.verify(posts).findByIdForUpdate(id);
		}
		verify(posts, org.mockito.Mockito.times(4)).findByIdForUpdate(anyLong());
	}
}
