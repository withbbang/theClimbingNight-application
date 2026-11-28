package com.theclimbingnight.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
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
class PostProfileGridRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");
	private static final List<PostStatus> OWNER = List.of(PostStatus.VISIBLE, PostStatus.HIDDEN_PENDING,
			PostStatus.HIDDEN);

	@Autowired PostRepository posts;
	@Autowired UserRepository users;

	private long user(String nickname) {
		return users.saveAndFlush(User.create(nickname)).getId();
	}

	private Post visible(long authorId, Instant publishedAt) {
		Post post = Post.draft(authorId);
		post.publish("본문");
		post.markVisible(publishedAt);
		return posts.saveAndFlush(post);
	}

	private Post hidden(long authorId, Instant publishedAt, boolean pending) {
		Post post = visible(authorId, publishedAt);
		post.hide(pending);
		return posts.saveAndFlush(post);
	}

	private void everyStatus(long author) {
		visible(author, T0);
		hidden(author, T0.minusSeconds(1), false);
		hidden(author, T0.minusSeconds(2), true);
		Post pending = Post.draft(author);
		pending.publish("대기");
		posts.saveAndFlush(pending);
		Post failed = Post.draft(author);
		failed.publish("실패");
		failed.markFailed();
		posts.saveAndFlush(failed);
		posts.saveAndFlush(Post.draft(author));
		Post deleted = visible(author, T0.plusSeconds(9));
		deleted.softDelete(T0);
		posts.saveAndFlush(deleted);
	}

	@Test
	void 남에게는_지워지지_않은_VISIBLE만_보인다() {
		// given
		long author = user("작성자");
		long other = user("다른이");
		everyStatus(author);
		visible(other, T0.plusSeconds(1));

		// when
		List<Post> grid = posts.findProfileGrid(author, List.of(PostStatus.VISIBLE), null, null, PageRequest.of(0, 20));

		// then
		assertThat(grid).hasSize(1).allSatisfy(p -> {
			assertThat(p.getAuthorId()).isEqualTo(author);
			assertThat(p.getStatus()).isEqualTo(PostStatus.VISIBLE);
			assertThat(p.getDeletedAt()).isNull();
		});
	}

	@Test
	void 본인에게는_VISIBLE_HIDDEN_PENDING_HIDDEN_셋만_보인다() {
		// given
		long author = user("작성자");
		everyStatus(author);

		// when
		List<Post> grid = posts.findProfileGrid(author, OWNER, null, null, PageRequest.of(0, 20));

		// then
		assertThat(grid).extracting(Post::getStatus).containsExactly(PostStatus.VISIBLE, PostStatus.HIDDEN,
				PostStatus.HIDDEN_PENDING);
	}

	@Test
	void 커서_뒤를_게시_시각_id_내림차순으로_주고_같은_시각은_id로_가른다() {
		// given
		long author = user("작성자");
		Post oldest = visible(author, T0.minusSeconds(30));
		Post tieLow = visible(author, T0);
		Post tieHigh = visible(author, T0);
		Post newest = visible(author, T0.plusSeconds(10));

		// when
		List<Post> first = posts.findProfileGrid(author, List.of(PostStatus.VISIBLE), null, null, PageRequest.of(0, 2));
		List<Post> afterTieHigh = posts.findProfileGrid(author, List.of(PostStatus.VISIBLE), T0, tieHigh.getId(),
				PageRequest.of(0, 10));
		List<Post> afterTieLow = posts.findProfileGrid(author, List.of(PostStatus.VISIBLE), T0, tieLow.getId(),
				PageRequest.of(0, 10));

		// then
		assertThat(first).containsExactly(newest, tieHigh);
		assertThat(afterTieHigh).containsExactly(tieLow, oldest);
		assertThat(afterTieLow).containsExactly(oldest);
	}

	@Test
	void 글_수는_지워진_VISIBLE을_세지_않는다() {
		// given
		long author = user("작성자");
		everyStatus(author);
		visible(author, T0.plusSeconds(1));

		// when / then
		assertThat(posts.countByAuthorIdAndStatusAndDeletedAtIsNull(author, PostStatus.VISIBLE)).isEqualTo(2);
	}
}
