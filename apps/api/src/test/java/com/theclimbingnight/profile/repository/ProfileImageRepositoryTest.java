package com.theclimbingnight.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.List;
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

	private User user(String nickname) {
		return users.saveAndFlush(User.create(nickname));
	}

	private ProfileImage image(long userId, String key) {
		return images.saveAndFlush(ProfileImage.start(userId, "uploads/" + key + ".jpg", "a/" + key, 1000));
	}

	@Test
	void 지금_사진이_READY일_때만_키를_준다() {
		// given
		User ready = user("준비됨");
		ProfileImage readyImage = image(ready.getId(), "r");
		readyImage.markReady();
		images.saveAndFlush(readyImage);
		ready.useProfileImage(readyImage.getId());

		User processing = user("처리중");
		ProfileImage processingImage = image(processing.getId(), "p");
		processingImage.markProcessing();
		images.saveAndFlush(processingImage);
		processing.useProfileImage(processingImage.getId());

		User removed = user("제거됨");
		ProfileImage removedImage = image(removed.getId(), "x");
		removedImage.markRemoved();
		images.saveAndFlush(removedImage);
		removed.useProfileImage(removedImage.getId());

		User noPointer = user("포인터없음");
		ProfileImage orphan = image(noPointer.getId(), "o");
		orphan.markReady();
		images.saveAndFlush(orphan);

		users.saveAllAndFlush(List.of(ready, processing, removed, noPointer));

		// when
		List<ProfileImageRepository.CurrentImage> found = images.findCurrentReady(
				List.of(ready.getId(), processing.getId(), removed.getId(), noPointer.getId()));

		// then
		assertThat(found).containsExactly(new ProfileImageRepository.CurrentImage(ready.getId(), "a/r"));
	}
}
