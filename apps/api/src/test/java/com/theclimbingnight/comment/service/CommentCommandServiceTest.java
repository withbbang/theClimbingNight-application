package com.theclimbingnight.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.comment.dto.CommentCreateRequest;
import com.theclimbingnight.comment.dto.CommentDisplay;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.feed.service.PostAccess;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.Role;
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
class CommentCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
	private static final long ME = 1L;
	private static final long POST_AUTHOR = 2L;
	private static final long OTHER = 3L;
	private static final long POST = 5L;

	@Mock PostRepository posts;
	@Mock UserBlockRepository blocks;
	@Mock CommentRepository comments;
	@Mock UserRepository users;
	@Mock CommentCounter counter;

	private CommentCommandService service;

	@BeforeEach
	void setUp() {
		service = new CommentCommandService(new PostAccess(posts, blocks), comments, users, counter, blocks,
				Clock.fixed(NOW, ZoneOffset.UTC));
		lenient().when(users.findAllByIdForShare(anyCollection())).thenAnswer(inv -> {
			Collection<Long> ids = inv.getArgument(0);
			return ids.stream().map(CommentCommandServiceTest::user).toList();
		});
		lenient().when(comments.findAuthorIdById(anyLong())).thenReturn(Optional.of(OTHER));
	}

	/** 넘긴 id 모음이 이 순서와 같은지. 서비스는 TreeSet을 넘긴다. */
	private static Collection<Long> ids(Long... expected) {
		return org.mockito.ArgumentMatchers.argThat(c -> List.copyOf(c).equals(List.of(expected)));
	}

	private static User user(long id) {
		User user = User.create("u" + id);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Post post(PostStatus status) {
		Post post = Post.draft(POST_AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	private Comment comment(long id, long postId, long authorId, Long parentId) {
		Comment comment = Comment.create(postId, authorId, parentId, null, "본문");
		ReflectionTestUtils.setField(comment, "id", id);
		ReflectionTestUtils.setField(comment, "createdAt", NOW);
		return comment;
	}

	private void givenPost(Post post) {
		given(posts.findByIdForUpdate(POST)).willReturn(Optional.of(post));
	}

	private void givenSave() {
		given(comments.save(any(Comment.class))).willAnswer(inv -> {
			Comment c = inv.getArgument(0);
			ReflectionTestUtils.setField(c, "id", 100L);
			ReflectionTestUtils.setField(c, "createdAt", NOW);
			return c;
		});
		given(users.findById(ME)).willReturn(Optional.of(User.create("나")));
	}

	@Test
	void 댓글을_달면_수를_다시_센다() {
		// given
		Post post = post(PostStatus.VISIBLE);
		givenPost(post);
		givenSave();

		// when
		CommentResponse response = service.create(ME, POST, new CommentCreateRequest("안녕", null));

		// then
		verify(counter).recount(post);
		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.parentId()).isNull();
		assertThat(response.author().nickname()).isEqualTo("나");
		assertThat(response.display()).isEqualTo(CommentDisplay.VISIBLE);
		assertThat(response.replyCount()).isZero();
		assertThat(response.mine()).isTrue();
		assertThat(response.deletable()).isTrue();
	}

	@Test
	void 본문은_앞뒤_공백을_자른다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		givenSave();

		// when
		CommentResponse response = service.create(ME, POST, new CommentCreateRequest("  안녕 \n", null));

		// then
		assertThat(response.body()).isEqualTo("안녕");
	}

	@Test
	void 답글에_답하면_같은_부모_아래에_붙고_대상은_답한_댓글_작성자다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		givenSave();
		given(comments.findById(20L)).willReturn(Optional.of(comment(20L, POST, OTHER, 10L)));
		given(users.findById(OTHER)).willReturn(Optional.of(User.create("상대")));

		// when
		CommentResponse response = service.create(ME, POST, new CommentCreateRequest("답", 20L));

		// then
		assertThat(response.parentId()).isEqualTo(10L);
		assertThat(response.replyToNickname()).isEqualTo("상대");
		verify(comments).save(org.mockito.ArgumentMatchers.argThat((Comment c) -> c.getParentId() == 10L
				&& c.getReplyToUserId() == OTHER));
	}

	@Test
	void 답글은_두_사용자_행을_id_오름차순으로_공유_잠금한_뒤_게시물과_대상_댓글을_읽는다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		given(comments.save(any(Comment.class))).willAnswer(inv -> {
			Comment c = inv.getArgument(0);
			ReflectionTestUtils.setField(c, "id", 100L);
			ReflectionTestUtils.setField(c, "createdAt", NOW);
			return c;
		});
		given(comments.findById(20L)).willReturn(Optional.of(comment(20L, POST, OTHER, null)));

		// when
		service.create(OTHER + 10, POST, new CommentCreateRequest("답", 20L));

		// then
		InOrder order = inOrder(comments, users, posts);
		order.verify(comments).findAuthorIdById(20L);
		order.verify(users).findAllByIdForShare(ids(OTHER, OTHER + 10));
		order.verify(posts).findByIdForUpdate(POST);
		order.verify(comments).findById(20L);
		order.verify(comments).save(any(Comment.class));
	}

	@Test
	void 자기_댓글에_답하면_사용자_행은_한_번만_잠근다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		givenSave();
		given(comments.findAuthorIdById(20L)).willReturn(Optional.of(ME));
		given(comments.findById(20L)).willReturn(Optional.of(comment(20L, POST, ME, null)));

		// when
		service.create(ME, POST, new CommentCreateRequest("답", 20L));

		// then
		verify(users).findAllByIdForShare(ids(ME));
	}

	@Test
	void 그새_탈퇴한_사용자는_댓글을_달_수_없다() {
		// given
		User withdrawn = user(ME);
		withdrawn.withdraw(NOW);
		given(users.findAllByIdForShare(ids(ME))).willReturn(List.of(withdrawn));

		// when / then
		assertThatThrownBy(() -> service.create(ME, POST, new CommentCreateRequest("안녕", null)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(posts, never()).findByIdForUpdate(POST);
	}

	@Test
	void 없는_댓글에_답하면_게시물을_잠그기_전에_404다() {
		// given
		given(comments.findAuthorIdById(20L)).willReturn(Optional.empty());

		// when / then
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("답", 20L)));
		verify(posts, never()).findByIdForUpdate(POST);
	}

	@Test
	void 다른_게시물_댓글에는_답할_수_없다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		given(comments.findById(20L)).willReturn(Optional.of(comment(20L, 99L, OTHER, null)));

		// when / then
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("답", 20L)));
		verify(comments, never()).save(any());
	}

	@Test
	void 지웠거나_숨긴_댓글에는_답할_수_없다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		Comment deleted = comment(20L, POST, OTHER, null);
		deleted.softDelete(NOW);
		Comment hidden = comment(21L, POST, OTHER, null);
		hidden.hide(false);
		given(comments.findById(20L)).willReturn(Optional.of(deleted));
		given(comments.findById(21L)).willReturn(Optional.of(hidden));

		// when / then
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("답", 20L)));
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("답", 21L)));
	}

	@Test
	void 차단_관계인_사람_댓글에는_답할_수_없다() {
		// given
		givenPost(post(PostStatus.VISIBLE));
		given(comments.findById(20L)).willReturn(Optional.of(comment(20L, POST, OTHER, null)));
		given(blocks.existsBetween(ME, POST_AUTHOR)).willReturn(false);
		given(blocks.existsBetween(ME, OTHER)).willReturn(true);

		// when / then
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("답", 20L)));
	}

	@Test
	void 답글에_답할_때_부모_댓글_작성자와_차단_관계면_404다() {
		// given: 답글 20의 작성자는 OTHER, 부모 10의 작성자는 parentAuthor
		long parentAuthor = OTHER + 1;
		givenPost(post(PostStatus.VISIBLE));
		given(comments.findById(20L)).willReturn(Optional.of(comment(20L, POST, OTHER, 10L)));
		given(comments.findAuthorIdById(10L)).willReturn(Optional.of(parentAuthor));
		given(blocks.existsBetween(ME, POST_AUTHOR)).willReturn(false);
		given(blocks.existsBetween(ME, OTHER)).willReturn(false);
		given(blocks.existsBetween(ME, parentAuthor)).willReturn(true);

		// when / then
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("답", 20L)));
		verify(comments, never()).save(any());
	}

	@Test
	void 보이지_않는_글에는_댓글을_달_수_없다() {
		// given
		givenPost(post(PostStatus.HIDDEN));

		// when / then
		assertNotFound(() -> service.create(ME, POST, new CommentCreateRequest("안녕", null)));
		verify(comments, never()).save(any());
	}

	private void givenDeletable(Comment comment, Post post) {
		given(comments.findLivePostIdById(comment.getId())).willReturn(Optional.of(POST));
		givenPost(post);
		given(comments.findByIdForUpdate(comment.getId())).willReturn(Optional.of(comment));
	}

	@Test
	void 게시물_작성자는_남의_댓글을_지울_수_있다() {
		// given
		Comment comment = comment(20L, POST, OTHER, null);
		Post post = post(PostStatus.VISIBLE);
		givenDeletable(comment, post);

		// when
		service.delete(POST_AUTHOR, Role.USER, 20L);

		// then
		assertThat(comment.getDeletedAt()).isEqualTo(NOW);
		verify(counter).recount(post);
	}

	@Test
	void 작성자_본인은_자기_댓글을_지울_수_있다() {
		// given
		Comment comment = comment(20L, POST, ME, null);
		Post post = post(PostStatus.VISIBLE);
		givenDeletable(comment, post);

		// when
		service.delete(ME, Role.USER, 20L);

		// then
		assertThat(comment.getDeletedAt()).isEqualTo(NOW);
		verify(counter).recount(post);
	}

	@Test
	void 잠근_뒤_이미_지워졌으면_404다() {
		// given
		Comment comment = comment(20L, POST, ME, null);
		comment.softDelete(NOW);
		given(comments.findLivePostIdById(20L)).willReturn(Optional.of(POST));
		givenPost(post(PostStatus.VISIBLE));
		given(comments.findByIdForUpdate(20L)).willReturn(Optional.of(comment));

		// when / then
		assertNotFound(() -> service.delete(ME, Role.USER, 20L));
		verify(counter, never()).recount(any());
	}

	@Test
	void 관리자는_어떤_댓글이든_지울_수_있다() {
		// given
		Comment comment = comment(20L, POST, OTHER, null);
		givenDeletable(comment, post(PostStatus.HIDDEN));

		// when
		service.delete(ME, Role.ADMIN, 20L);

		// then
		assertThat(comment.getDeletedAt()).isEqualTo(NOW);
	}

	@Test
	void 권한_없는_사람이_지우면_403이다() {
		// given
		Comment comment = comment(20L, POST, OTHER, null);
		givenDeletable(comment, post(PostStatus.VISIBLE));

		// when / then
		assertThatThrownBy(() -> service.delete(ME, Role.MEMBER, 20L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN));
		assertThat(comment.getDeletedAt()).isNull();
		verify(counter, never()).recount(any());
	}

	@Test
	void 지운_댓글을_다시_지우면_404다() {
		// given
		Comment comment = comment(20L, POST, ME, null);
		comment.softDelete(NOW);
		given(comments.findLivePostIdById(20L)).willReturn(Optional.empty());

		// when / then
		assertNotFound(() -> service.delete(ME, Role.USER, 20L));
	}

	private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}
}
