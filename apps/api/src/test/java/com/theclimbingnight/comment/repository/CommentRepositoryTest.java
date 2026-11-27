package com.theclimbingnight.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.moderation.entity.UserBlock;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

	@Autowired CommentRepository comments;
	@Autowired PostRepository posts;
	@Autowired UserRepository users;
	@Autowired UserBlockRepository blocks;

	private long post(long authorId) {
		Post post = Post.draft(authorId);
		post.publish("본문");
		post.markVisible(T0);
		return posts.saveAndFlush(post).getId();
	}

	@Test
	void 살아있는_댓글의_게시물_id만_찾고_지운_댓글은_비어_있다() {
		// given
		long author = users.saveAndFlush(User.create("작성자")).getId();
		long postId = post(author);
		Comment live = comments.saveAndFlush(Comment.create(postId, author, null, null, "살아있음"));
		Comment deleted = comments.saveAndFlush(Comment.create(postId, author, null, null, "지움"));
		deleted.softDelete(T0);
		comments.flush();

		// when / then
		assertThat(comments.findLivePostIdById(live.getId())).contains(postId);
		assertThat(comments.findLivePostIdById(deleted.getId())).isEmpty();
	}

	@Test
	void 수는_지우거나_숨긴_댓글을_빼고_답글을_포함한다() {
		// given
		long author = users.saveAndFlush(User.create("작성자")).getId();
		long postId = post(author);
		long otherPost = post(author);
		Comment top = comments.saveAndFlush(Comment.create(postId, author, null, null, "최상위"));
		comments.saveAndFlush(Comment.create(postId, author, top.getId(), author, "답글"));
		Comment deleted = comments.saveAndFlush(Comment.create(postId, author, null, null, "지움"));
		deleted.softDelete(T0);
		Comment pending = comments.saveAndFlush(Comment.create(postId, author, null, null, "대기"));
		pending.hide(true);
		Comment hidden = comments.saveAndFlush(Comment.create(postId, author, null, null, "숨김"));
		hidden.hide(false);
		comments.saveAndFlush(Comment.create(otherPost, author, null, null, "다른 글"));
		comments.flush();

		// when
		int count = comments.countShownByPostId(postId);

		// then
		assertThat(count).isEqualTo(2);
	}

	private long user(String nickname) {
		return users.saveAndFlush(User.create(nickname)).getId();
	}

	private Comment save(long postId, long authorId, Long parentId, String body) {
		return comments.saveAndFlush(Comment.create(postId, authorId, parentId, null, body));
	}

	private List<Long> top(long postId, long viewer, Long cursorId) {
		return comments.findTopLevel(postId, viewer, cursorId, PageRequest.of(0, 50)).stream().map(Comment::getId)
				.toList();
	}

	@Test
	void 최상위_댓글은_오래된_순이고_커서_뒤만_준다() {
		long author = user("작성자");
		long postId = post(author);
		Comment a = save(postId, author, null, "a");
		Comment b = save(postId, author, null, "b");
		Comment c = save(postId, author, null, "c");
		save(postId, author, a.getId(), "답글");

		assertThat(top(postId, author, null)).containsExactly(a.getId(), b.getId(), c.getId());
		assertThat(top(postId, author, a.getId())).containsExactly(b.getId(), c.getId());
		assertThat(comments.findTopLevel(postId, author, null, PageRequest.of(0, 2))).hasSize(2);
	}

	@Test
	void 보이는_답글이_남은_지운_댓글은_자리로_남는다() {
		long author = user("작성자");
		long postId = post(author);
		long viewer = user("보는이");
		Comment parent = save(postId, author, null, "부모");
		save(postId, author, parent.getId(), "답글");
		parent.softDelete(T0);
		Comment hiddenParent = save(postId, author, null, "숨김 부모");
		save(postId, author, hiddenParent.getId(), "답글2");
		hiddenParent.hide(false);
		comments.flush();

		assertThat(top(postId, viewer, null)).containsExactly(parent.getId(), hiddenParent.getId());
	}

	@Test
	void 답글이_모두_지워지면_지운_부모도_빠진다() {
		long author = user("작성자");
		long postId = post(author);
		long viewer = user("보는이");
		Comment parent = save(postId, author, null, "부모");
		Comment reply = save(postId, author, parent.getId(), "답글");
		parent.softDelete(T0);
		reply.softDelete(T0);
		comments.flush();

		assertThat(top(postId, viewer, null)).isEmpty();
	}

	@Test
	void 숨긴_댓글은_작성자에게만_나온다() {
		long author = user("작성자");
		long postId = post(author);
		long writer = user("쓴이");
		long viewer = user("보는이");
		Comment hidden = save(postId, writer, null, "숨김");
		hidden.hide(false);
		Comment hiddenReply = save(postId, writer, save(postId, author, null, "부모").getId(), "숨긴 답글");
		hiddenReply.hide(true);
		Comment deletedHidden = save(postId, writer, null, "지우고 숨김");
		deletedHidden.hide(false);
		deletedHidden.softDelete(T0);
		comments.flush();

		assertThat(top(postId, writer, null)).contains(hidden.getId()).doesNotContain(deletedHidden.getId());
		assertThat(top(postId, viewer, null)).doesNotContain(hidden.getId());
		assertThat(comments.findReplies(hiddenReply.getParentId(), writer, null, PageRequest.of(0, 50)))
				.containsExactly(hiddenReply);
		assertThat(comments.findReplies(hiddenReply.getParentId(), viewer, null, PageRequest.of(0, 50))).isEmpty();
	}

	@Test
	void 차단_관계인_사람의_최상위_댓글은_빠진다() {
		long author = user("작성자");
		long postId = post(author);
		long viewer = user("보는이");
		long blockedByMe = user("내가차단");
		long blockingMe = user("나를차단");
		long normal = user("보통");
		blocks.saveAndFlush(UserBlock.of(viewer, blockedByMe, T0));
		blocks.saveAndFlush(UserBlock.of(blockingMe, viewer, T0));
		save(postId, blockedByMe, null, "내가 차단");
		save(postId, blockingMe, null, "나를 차단");
		Comment ok = save(postId, normal, null, "보통");
		Comment replyOfBlocked = save(postId, normal, save(postId, blockedByMe, null, "x").getId(), "답글");

		assertThat(top(postId, viewer, null)).containsExactly(ok.getId());
		assertThat(comments.findReplies(replyOfBlocked.getParentId(), viewer, null, PageRequest.of(0, 50))).hasSize(1);
	}

	@Test
	void 남은_답글이_차단_관계인_사람_것뿐이면_지운_부모_자리도_빠진다() {
		// given
		long author = user("작성자");
		long postId = post(author);
		long viewer = user("보는이");
		long blockedByMe = user("내가차단");
		long blockingMe = user("나를차단");
		long other = user("남");
		blocks.saveAndFlush(UserBlock.of(viewer, blockedByMe, T0));
		blocks.saveAndFlush(UserBlock.of(blockingMe, viewer, T0));
		Comment parentA = save(postId, author, null, "부모A");
		save(postId, blockedByMe, parentA.getId(), "내가 차단한 사람 답글");
		parentA.softDelete(T0);
		Comment parentB = save(postId, author, null, "부모B");
		save(postId, blockingMe, parentB.getId(), "나를 차단한 사람 답글");
		parentB.hide(false);
		comments.flush();

		// when / then
		assertThat(top(postId, viewer, null)).isEmpty();
		assertThat(top(postId, other, null)).containsExactly(parentA.getId(), parentB.getId());
	}

	@Test
	void 답글_수는_보이는_답글만_센다() {
		long author = user("작성자");
		long postId = post(author);
		Comment p1 = save(postId, author, null, "p1");
		Comment p2 = save(postId, author, null, "p2");
		save(postId, author, p1.getId(), "r1");
		save(postId, author, p1.getId(), "r2");
		save(postId, author, p1.getId(), "지움").softDelete(T0);
		save(postId, author, p1.getId(), "숨김").hide(false);
		comments.flush();

		List<ReplyCount> counts = comments.countShownReplies(List.of(p1.getId(), p2.getId()));

		assertThat(counts).extracting(ReplyCount::parentId, ReplyCount::count).containsExactly(
				org.assertj.core.groups.Tuple.tuple(p1.getId(), 2L));
	}
}
