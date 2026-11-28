package com.theclimbingnight.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProfileImageRepositoryTest {

	@Autowired ProfileImageRepository images;
	@Autowired UserRepository users;

	@Test
	void existsNewerConfirmed() {
		// given
		long userId = users.saveAndFlush(User.create("등반가")).getId();
		ProfileImage first = processing(userId, "a");
		ProfileImage second = processing(userId, "b");

		// when / then
		assertThat(images.existsByUserIdAndStatusAndIdGreaterThan(userId, ProfileImageStatus.PROCESSING, first.getId()))
				.isTrue();
		assertThat(images.existsByUserIdAndStatusAndIdGreaterThan(userId, ProfileImageStatus.PROCESSING, second.getId()))
				.isFalse();
	}

	private ProfileImage processing(long userId, String key) {
		ProfileImage image = ProfileImage.start(userId, "uploads/" + key + ".jpg", "a/" + key, 1000);
		image.markProcessing();
		return images.saveAndFlush(image);
	}
}
