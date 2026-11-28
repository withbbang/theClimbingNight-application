package com.theclimbingnight.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.config.JpaAuditingConfig;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({JpaAuditingConfig.class, ProfileImageCleanupJob.class, ProfileImageCleanupJobTest.TestClock.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProfileImageCleanupJobTest {

	private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");

	@TestConfiguration
	static class TestClock {
		@Bean
		Clock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired ProfileImageCleanupJob job;
	@Autowired ProfileImageRepository images;
	@Autowired UserRepository users;
	@Autowired JdbcTemplate jdbc;
	@Autowired jakarta.persistence.EntityManager em;

	private ProfileImage imageCreatedAt(long userId, String tag, Instant createdAt) {
		ProfileImage image = images.saveAndFlush(ProfileImage.start(userId, "uploads/" + tag + ".jpg", "a/" + tag, 10L));
		jdbc.update("update profile_images set created_at = ? where id = ?",
				LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC), image.getId());
		return image;
	}

	@Test
	void 만든_지_24시간_넘은_UPLOADING만_지운다() {
		// given
		long user = users.saveAndFlush(User.create("정리대상")).getId();
		ProfileImage old = imageCreatedAt(user, "old", NOW.minus(Duration.ofHours(25)));
		ProfileImage fresh = imageCreatedAt(user, "fresh", NOW.minus(Duration.ofHours(23)));
		ProfileImage processing = imageCreatedAt(user, "proc", NOW.minus(Duration.ofHours(48)));
		jdbc.update("update profile_images set status = 'PROCESSING' where id = ?", processing.getId());
		em.clear();

		// when
		job.run();

		// then
		images.flush();
		assertThat(images.findAllById(List.of(old.getId(), fresh.getId(), processing.getId())))
				.extracting(ProfileImage::getId).containsExactlyInAnyOrder(fresh.getId(), processing.getId());
		assertThat(images.findById(processing.getId()).orElseThrow().getStatus())
				.isEqualTo(ProfileImageStatus.PROCESSING);
	}
}
