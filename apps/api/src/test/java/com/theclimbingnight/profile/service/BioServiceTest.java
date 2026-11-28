package com.theclimbingnight.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.profile.dto.ProfileResponse;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BioServiceTest {

	@Mock UserRepository users;
	@Mock PostRepository posts;
	@Mock UserAvatars avatars;

	@Test
	void 자기소개를_바꾼_뒤_돌려주는_글_수는_내_격자와_같은_상태를_센다() {
		// given
		User me = User.create("climber1");
		ReflectionTestUtils.setField(me, "id", 1L);
		given(users.findByIdForUpdate(1L)).willReturn(Optional.of(me));
		given(posts.countByAuthorIdAndStatusInAndDeletedAtIsNull(1L,
				List.of(PostStatus.VISIBLE, PostStatus.HIDDEN_PENDING, PostStatus.HIDDEN))).willReturn(3L);
		BioService service = new BioService(users, posts, new BioPolicy(), avatars);

		// when
		ProfileResponse response = service.update(1L, "안녕하세요");

		// then
		assertThat(response.bio()).isEqualTo("안녕하세요");
		assertThat(response.postCount()).isEqualTo(3);
	}
}
