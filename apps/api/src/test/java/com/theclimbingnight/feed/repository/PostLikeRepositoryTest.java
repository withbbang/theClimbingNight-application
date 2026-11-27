package com.theclimbingnight.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostLike;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostLikeRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

	@Autowired PostLikeRepository likes;
	@Autowired PostRepository posts;
	@Autowired UserRepository users;
	@Autowired UserBlockRepository blocks;

	private long user(String nickname) {
		return users.saveAndFlush(User.create(nickname)).getId();
	}

	private long post(long authorId) {
		Post post = Post.draft(authorId);
		post.publish("본문");
		post.markVisible(T0);
		return posts.saveAndFlush(post).getId();
	}

	@Test
	void 같은_사람의_좋아요는_한_번만_저장된다() {
		// given
		long author = user("작성자");
		long postId = post(author);
		long liker = user("누른이");
		likes.saveAndFlush(PostLike.of(postId, liker, T0));

		// when / then
		assertThatThrownBy(() -> likes.saveAndFlush(PostLike.of(postId, liker, T0.plusSeconds(1))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 좋아요_누른_사람은_최신순이고_차단_관계는_빠진다() {
		// given
		long author = user("작성자");
		long postId = post(author);
		long viewer = user("보는이");
		long first = user("첫째");
		long second = user("둘째");
		long blocked = user("차단한이");
		likes.saveAndFlush(PostLike.of(postId, first, T0));
		likes.saveAndFlush(PostLike.of(postId, blocked, T0.plusSeconds(1)));
		likes.saveAndFlush(PostLike.of(postId, second, T0.plusSeconds(2)));
		blocks.saveAndFlush(UserBlock.of(viewer, blocked, T0));

		// when
		List<PostLike> result = likes.findLikers(postId, viewer, null, null, PageRequest.of(0, 10));

		// then
		assertThat(result).extracting(PostLike::getUserId).containsExactly(second, first);
	}

	@Test
	void 좋아요_목록_커서는_그_뒤만_준다() {
		// given
		long author = user("작성자");
		long postId = post(author);
		long viewer = user("보는이");
		long a = user("가");
		long b = user("나");
		long c = user("다");
		likes.saveAndFlush(PostLike.of(postId, a, T0));
		likes.saveAndFlush(PostLike.of(postId, b, T0.plusSeconds(1)));
		likes.saveAndFlush(PostLike.of(postId, c, T0.plusSeconds(1)));

		// when
		List<PostLike> all = likes.findLikers(postId, viewer, null, null, PageRequest.of(0, 10));
		List<PostLike> afterC = likes.findLikers(postId, viewer, T0.plusSeconds(1), c, PageRequest.of(0, 10));

		// then
		assertThat(all).extracting(PostLike::getUserId).containsExactly(c, b, a);
		assertThat(afterC).extracting(PostLike::getUserId).containsExactly(b, a);
	}

	@Test
	void 내가_좋아요한_게시물_id만_돌려준다() {
		// given
		long author = user("작성자");
		long liked1 = post(author);
		long liked2 = post(author);
		long notLiked = post(author);
		long me = user("나");
		long other = user("남");
		likes.saveAndFlush(PostLike.of(liked1, me, T0));
		likes.saveAndFlush(PostLike.of(liked2, me, T0));
		likes.saveAndFlush(PostLike.of(notLiked, other, T0));

		// when
		List<Long> ofPage = likes.findLikedPostIds(me, List.of(liked1, notLiked));
		List<Long> all = likes.findPostIdsByUserId(me);

		// then
		assertThat(ofPage).containsExactly(liked1);
		assertThat(all).containsExactlyInAnyOrder(liked1, liked2);
		assertThat(likes.existsByPostIdAndUserId(liked1, me)).isTrue();
		assertThat(likes.countByPostId(notLiked)).isEqualTo(1);
		assertThat(likes.deleteLike(liked1, me)).isEqualTo(1);
		assertThat(likes.deleteLike(liked1, me)).isZero();
	}
}
