package com.theclimbingnight.feed.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.config.JpaAuditingConfig;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({JpaAuditingConfig.class, DraftPostCleanupJob.class, DraftPostCleanupJobTest.TestClock.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DraftPostCleanupJobTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

	@TestConfiguration
	static class TestClock {
		@Bean
		Clock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired DraftPostCleanupJob job;
	@Autowired PostRepository posts;
	@Autowired PostMediaRepository media;
	@Autowired UserRepository users;
	@Autowired JdbcTemplate jdbc;
	@Autowired jakarta.persistence.EntityManager em;

	private Post draftCreatedAt(long authorId, Instant createdAt) {
		Post post = posts.saveAndFlush(Post.draft(authorId));
		media.saveAndFlush(PostMedia.image(post.getId(), 0, "uploads/" + post.getId() + ".jpg", "p/" + post.getId()));
		jdbc.update("update posts set created_at = ? where id = ?", java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC), post.getId());
		return post;
	}

	@Test
	void 만든_지_24시간_넘은_초안만_지운다() {
		// given
		long author = users.saveAndFlush(User.create("초안작성자")).getId();
		Post old = draftCreatedAt(author, NOW.minus(Duration.ofHours(24)).minusSeconds(1));
		Post exactly = draftCreatedAt(author, NOW.minus(Duration.ofHours(24)));
		Post fresh = draftCreatedAt(author, NOW.minus(Duration.ofHours(1)));
		Post published = draftCreatedAt(author, NOW.minus(Duration.ofHours(48)));
		jdbc.update("update posts set status = 'PENDING' where id = ?", published.getId());

		em.clear();

		// when
		job.run();

		// then
		posts.flush();
		assertThat(posts.findAllById(java.util.List.of(old.getId(), exactly.getId(), fresh.getId(), published.getId())))
				.extracting(Post::getId).containsExactlyInAnyOrder(exactly.getId(), fresh.getId(), published.getId());
		assertThat(media.findAllByPostIdOrderBySortOrder(old.getId())).isEmpty();
		assertThat(media.findAllByPostIdOrderBySortOrder(exactly.getId())).hasSize(1);
		assertThat(media.findAllByPostIdOrderBySortOrder(published.getId())).hasSize(1);
	}
}
