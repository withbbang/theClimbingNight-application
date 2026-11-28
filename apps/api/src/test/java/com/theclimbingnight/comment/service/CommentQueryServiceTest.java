package com.theclimbingnight.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.theclimbingnight.comment.dto.CommentDisplay;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.repository.ReplyCount;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.CursorResponse;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.feed.service.PostAccess;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
	private static final long ME = 1L;
	private static final long POST_AUTHOR = 2L;
	private static final long OTHER = 3L;
	private static final long POST = 5L;

	@Mock PostRepository posts;
	@Mock UserBlockRepository blocks;
	@Mock CommentRepository comments;
	@Mock UserRepository users;
	@Mock UserAvatars avatars;

	private CommentQueryService service;

	@BeforeEach
	void setUp() {
		service = new CommentQueryService(new PostAccess(posts, blocks), comments, users, blocks, avatars);
	}

	private Post givenPost(long authorId, PostStatus status) {
		Post post = Post.draft(authorId);
		ReflectionTestUtils.setField(post, "id", POST);
		ReflectionTestUtils.setField(post, "status", status);
		given(posts.findById(POST)).willReturn(Optional.of(post));
		return post;
	}

	private Comment comment(long id, long authorId, Long parentId, Long replyToUserId) {
		Comment c = Comment.create(POST, authorId, parentId, replyToUserId, "본문" + id);
		ReflectionTestUtils.setField(c, "id", id);
		ReflectionTestUtils.setField(c, "createdAt", NOW);
		return c;
	}

	private User user(long id, String nickname) {
		User u = User.create(nickname);
		ReflectionTestUtils.setField(u, "id", id);
		return u;
	}

	private void givenTop(Comment... found) {
		given(comments.findTopLevel(eq(POST), eq(ME), any(), any())).willReturn(List.of(found));
		if (found.length > 0) {
			given(comments.countShownReplies(anyCollection())).willReturn(List.of());
		}
	}

	@Test
	void authorHasProfileImageUrl() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		givenTop(comment(10, OTHER, null, null), comment(11, ME, null, null));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남"), user(ME, "나")));
		String small = "https://m.example/a/ab12/s.webp";
		given(avatars.of(anyCollection()))
				.willReturn(Map.of(OTHER, new ProfileImageUrls("https://m.example/a/ab12/l.webp", small)));

		CursorResponse<CommentResponse> result = service.list(ME, Role.USER, POST, null, null, 20);

		assertThat(result.items().get(0).author().profileImageUrl()).isEqualTo(small);
		assertThat(result.items().get(1).author().profileImageUrl()).isNull();
	}

	@Test
	void 자리는_작성자와_본문이_없다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		Comment deleted = comment(10, OTHER, null, null);
		deleted.softDelete(NOW);
		Comment hidden = comment(11, OTHER, null, null);
		hidden.hide(false);
		givenTop(deleted, hidden);
		given(comments.countShownReplies(anyCollection())).willReturn(List.of(new ReplyCount(10L, 2L)));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		CursorResponse<CommentResponse> result = service.list(ME, Role.USER, POST, null, null, 20);

		CommentResponse d = result.items().get(0);
		assertThat(d.display()).isEqualTo(CommentDisplay.DELETED);
		assertThat(d.author()).isNull();
		assertThat(d.body()).isNull();
		assertThat(d.deletable()).isFalse();
		assertThat(d.replyCount()).isEqualTo(2);
		CommentResponse h = result.items().get(1);
		assertThat(h.display()).isEqualTo(CommentDisplay.HIDDEN);
		assertThat(h.author()).isNull();
		assertThat(h.body()).isNull();
	}

	@Test
	void 숨긴_내_댓글은_HIDDEN_MINE이다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		Comment mine = comment(10, ME, null, null);
		mine.hide(true);
		givenTop(mine);
		given(users.findAllById(anyCollection())).willReturn(List.of(user(ME, "나")));

		CommentResponse r = service.list(ME, Role.USER, POST, null, null, 20).items().get(0);

		assertThat(r.display()).isEqualTo(CommentDisplay.HIDDEN_MINE);
		assertThat(r.body()).isEqualTo("본문10");
		assertThat(r.author().nickname()).isEqualTo("나");
		assertThat(r.mine()).isTrue();
		assertThat(r.deletable()).isTrue();
	}

	@Test
	void 보이는_댓글의_deletable은_내_글_게시물_작성자_관리자만_참이다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		givenTop(comment(10, OTHER, null, null));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		assertThat(service.list(ME, Role.USER, POST, null, null, 20).items().get(0).deletable()).isFalse();
		assertThat(service.list(ME, Role.ADMIN, POST, null, null, 20).items().get(0).deletable()).isTrue();
	}

	@Test
	void 탈퇴한_사람에게_단_답글은_대상_닉네임이_null이다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		Comment parent = comment(10, OTHER, null, null);
		given(comments.findById(10L)).willReturn(Optional.of(parent));
		given(blocks.existsBetween(ME, POST_AUTHOR)).willReturn(false);
		given(blocks.existsBetween(ME, OTHER)).willReturn(false);
		User gone = user(4L, "탈퇴");
		gone.withdraw(NOW);
		Comment reply = comment(11, OTHER, 10L, 4L);
		given(comments.findReplies(eq(10L), eq(ME), any(), any())).willReturn(List.of(reply));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남"), gone));

		CommentResponse r = service.list(ME, Role.USER, POST, 10L, null, 20).items().get(0);

		assertThat(r.replyToNickname()).isNull();
		assertThat(r.parentId()).isEqualTo(10L);
		assertThat(r.replyCount()).isZero();
	}

	@Test
	void 차단_관계인_부모의_답글을_요청하면_404다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		given(comments.findById(10L)).willReturn(Optional.of(comment(10, OTHER, null, null)));
		given(blocks.existsBetween(ME, POST_AUTHOR)).willReturn(false);
		given(blocks.existsBetween(ME, OTHER)).willReturn(true);

		assertThatThrownBy(() -> service.list(ME, Role.USER, POST, 10L, null, 20))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 다른_게시물의_parentId는_404다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		Comment elsewhere = Comment.create(99L, OTHER, null, null, "x");
		ReflectionTestUtils.setField(elsewhere, "id", 10L);
		given(comments.findById(10L)).willReturn(Optional.of(elsewhere));

		assertThatThrownBy(() -> service.list(ME, Role.USER, POST, 10L, null, 20))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 답글의_parentId는_404다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		given(comments.findById(11L)).willReturn(Optional.of(comment(11, OTHER, 10L, null)));

		assertThatThrownBy(() -> service.list(ME, Role.USER, POST, 11L, null, 20))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void 숨겨진_내_글의_댓글은_작성자가_볼_수_있다() {
		givenPost(ME, PostStatus.HIDDEN);
		givenTop();

		assertThat(service.list(ME, Role.USER, POST, null, null, 20).items()).isEmpty();
	}

	@Test
	void 남의_숨겨진_글_댓글은_404다() {
		givenPost(POST_AUTHOR, PostStatus.HIDDEN);

		assertThatThrownBy(() -> service.list(ME, Role.USER, POST, null, null, 20))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 한_페이지_넘치면_다음_커서를_준다() {
		givenPost(POST_AUTHOR, PostStatus.VISIBLE);
		givenTop(comment(10, OTHER, null, null), comment(11, OTHER, null, null));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(OTHER, "남")));

		CursorResponse<CommentResponse> r = service.list(ME, Role.USER, POST, null, null, 1);

		assertThat(r.items()).hasSize(1);
		assertThat(r.nextCursor()).isNotNull();
	}
}
