package com.theclimbingnight.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.moderation.entity.UserBlock;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

	@Autowired PostRepository posts;
	@Autowired UserRepository users;
	@Autowired UserBlockRepository blocks;

	private long user(String nickname) {
		return users.saveAndFlush(User.create(nickname)).getId();
	}

	private Post visible(long authorId, Instant publishedAt) {
		Post post = Post.draft(authorId);
		post.publish("본문");
		post.markVisible(publishedAt);
		return posts.saveAndFlush(post);
	}

	@Test
	void 피드는_보이는_글만_게시_시각_최신순으로_준다() {
		// given
		long author = user("작성자");
		Post oldest = visible(author, T0.minusSeconds(30));
		Post newest = visible(author, T0);
		Post middle = visible(author, T0.minusSeconds(10));
		Post draft = posts.saveAndFlush(Post.draft(author));
		Post pending = Post.draft(author);
		pending.publish("대기");
		posts.saveAndFlush(pending);
		Post hidden = visible(author, T0.plusSeconds(5));
		hidden.hide(false);
		posts.saveAndFlush(hidden);
		Post deleted = visible(author, T0.plusSeconds(6));
		deleted.softDelete(T0);
		posts.saveAndFlush(deleted);

		// when
		List<Post> feed = posts.findFeed(null, null, null, PageRequest.of(0, 10));

		// then
		assertThat(feed).extracting(Post::getId).containsExactly(newest.getId(), middle.getId(), oldest.getId());
		assertThat(feed).doesNotContain(draft, pending, hidden, deleted);
	}

	@Test
	void 같은_게시_시각이면_id_내림차순이고_커서_다음부터_준다() {
		// given
		long author = user("작성자");
		Post a = visible(author, T0);
		Post b = visible(author, T0);
		Post c = visible(author, T0);
		Post older = visible(author, T0.minusSeconds(1));

		// when
		List<Post> all = posts.findFeed(null, null, null, PageRequest.of(0, 10));
		List<Post> afterB = posts.findFeed(null, T0, b.getId(), PageRequest.of(0, 10));
		List<Post> limited = posts.findFeed(null, null, null, PageRequest.of(0, 2));

		// then
		assertThat(all).extracting(Post::getId).containsExactly(c.getId(), b.getId(), a.getId(), older.getId());
		assertThat(afterB).extracting(Post::getId).containsExactly(a.getId(), older.getId());
		assertThat(limited).extracting(Post::getId).containsExactly(c.getId(), b.getId());
	}

	@Test
	void 내가_차단한_사람과_나를_차단한_사람의_글은_빠진다() {
		// given
		long viewer = user("보는이");
		long blockedByMe = user("차단한이");
		long blockingMe = user("차단당한이");
		long other = user("다른이");
		visible(blockedByMe, T0);
		visible(blockingMe, T0.minusSeconds(1));
		Post mine = visible(viewer, T0.minusSeconds(2));
		Post othersPost = visible(other, T0.minusSeconds(3));
		blocks.saveAndFlush(UserBlock.of(viewer, blockedByMe, T0));
		blocks.saveAndFlush(UserBlock.of(blockingMe, viewer, T0));

		// when
		List<Post> forViewer = posts.findFeed(viewer, null, null, PageRequest.of(0, 10));
		List<Post> forAnonymous = posts.findFeed(null, null, null, PageRequest.of(0, 10));

		// then
		assertThat(forViewer).extracting(Post::getId).containsExactly(mine.getId(), othersPost.getId());
		assertThat(forAnonymous).hasSize(4);
	}

	@Test
	void 비로그인_판정용_최신_3개는_차단과_무관하다() {
		// given
		long viewer = user("보는이");
		long author = user("작성자");
		visible(author, T0.minusSeconds(40));
		Post p3 = visible(author, T0.minusSeconds(30));
		Post p2 = visible(author, T0.minusSeconds(20));
		Post p1 = visible(author, T0.minusSeconds(10));
		blocks.saveAndFlush(UserBlock.of(viewer, author, T0));

		// when
		List<Long> ids = posts.findLatestVisibleIds(3);

		// then
		assertThat(ids).containsExactly(p1.getId(), p2.getId(), p3.getId());
	}

	@Test
	void 차단_관계는_어느_방향이든_찾는다() {
		// given
		long a = user("가");
		long b = user("나");
		long c = user("다");
		blocks.saveAndFlush(UserBlock.of(a, b, T0));

		// then
		assertThat(blocks.existsBetween(a, b)).isTrue();
		assertThat(blocks.existsBetween(b, a)).isTrue();
		assertThat(blocks.existsBetween(a, c)).isFalse();
	}

	@Test
	void 내_진행중_글은_PENDING과_FAILED만_최신순으로_준다() {
		// given
		long me = user("나");
		long other = user("남");
		Post pending = Post.draft(me);
		pending.publish("대기");
		posts.saveAndFlush(pending);
		Post failed = Post.draft(me);
		failed.publish("실패");
		failed.markFailed();
		posts.saveAndFlush(failed);
		visible(me, T0);
		posts.saveAndFlush(Post.draft(me));
		Post othersPending = Post.draft(other);
		othersPending.publish("남의 글");
		posts.saveAndFlush(othersPending);
		Post deleted = Post.draft(me);
		deleted.publish("삭제");
		deleted.softDelete(T0);
		posts.saveAndFlush(deleted);

		// when
		List<Post> inProgress = posts.findTop10ByAuthorIdAndStatusInAndDeletedAtIsNullOrderByIdDesc(me,
				List.of(com.theclimbingnight.feed.entity.PostStatus.PENDING,
						com.theclimbingnight.feed.entity.PostStatus.FAILED));

		// then
		assertThat(inProgress).extracting(Post::getId).containsExactly(failed.getId(), pending.getId());
	}
}
