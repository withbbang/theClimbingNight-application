package com.theclimbingnight.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
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

	private long post(long authorId) {
		Post post = Post.draft(authorId);
		post.publish("본문");
		post.markVisible(T0);
		return posts.saveAndFlush(post).getId();
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
}
