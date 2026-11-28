package com.theclimbingnight.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.moderation.dto.BlockedUserResponse;
import com.theclimbingnight.moderation.entity.UserBlock;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
	private static final long ME = 3L;
	private static final long TARGET = 9L;

	@Mock UserBlockRepository blocks;
	@Mock UserRepository users;
	@Mock UserAvatars avatars;

	private BlockService service;

	@BeforeEach
	void setUp() {
		service = new BlockService(blocks, users, avatars, new MutableClock(NOW));
	}

	private static User user(long id, String nickname) {
		User user = User.create(nickname);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private void givenUsers() {
		given(users.findByIdForUpdate(ME)).willReturn(Optional.of(user(ME, "나")));
		given(users.findById(TARGET)).willReturn(Optional.of(user(TARGET, "대상")));
	}

	@Test
	void 자기_자신_차단은_BLOCK_SELF다() {
		// when / then
		assertThatThrownBy(() -> service.block(ME, ME)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BLOCK_SELF));
		verifyNoInteractions(blocks);
	}

	@Test
	void 차단하면_저장하고_대상을_돌려준다() {
		// given
		givenUsers();
		given(blocks.findByBlockerIdAndBlockedId(ME, TARGET)).willReturn(Optional.empty());

		// when
		BlockedUserResponse response = service.block(ME, TARGET);

		// then
		ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
		verify(blocks).save(saved.capture());
		assertThat(saved.getValue().getBlockerId()).isEqualTo(ME);
		assertThat(saved.getValue().getBlockedId()).isEqualTo(TARGET);
		assertThat(response).isEqualTo(new BlockedUserResponse(TARGET, "대상", null, NOW.atOffset(ZoneOffset.UTC)));
	}

	@Test
	void 이미_차단했으면_다시_저장하지_않고_처음_차단_시각을_돌려준다() {
		// given
		givenUsers();
		Instant earlier = NOW.minusSeconds(3600);
		given(blocks.findByBlockerIdAndBlockedId(ME, TARGET)).willReturn(Optional.of(UserBlock.of(ME, TARGET, earlier)));

		// when
		BlockedUserResponse response = service.block(ME, TARGET);

		// then
		verify(blocks, never()).save(any());
		assertThat(response.blockedAt()).isEqualTo(earlier.atOffset(ZoneOffset.UTC));
	}

	@Test
	void 없거나_탈퇴한_사용자_차단은_404다() {
		// given
		given(users.findByIdForUpdate(ME)).willReturn(Optional.of(user(ME, "나")));
		User left = user(TARGET, "left");
		left.withdraw(NOW);
		given(users.findById(TARGET)).willReturn(Optional.of(left));
		given(users.findById(10L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.block(ME, TARGET)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThatThrownBy(() -> service.block(ME, 10L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		verify(blocks, never()).save(any());
	}

	@Test
	void 차단_해제는_내_차단만_지운다() {
		// when
		service.unblock(ME, TARGET);

		// then
		verify(blocks).deleteBlock(ME, TARGET);
	}

	@Test
	void 차단_목록은_최근순이고_닉네임을_붙이며_탈퇴한_상대는_뺀다() {
		// given
		given(blocks.findAllByBlockerIdOrderByCreatedAtDesc(ME)).willReturn(List.of(
				UserBlock.of(ME, TARGET, NOW), UserBlock.of(ME, 10L, NOW.minusSeconds(60))));
		User left = user(10L, "left");
		left.withdraw(NOW);
		given(users.findAllById(List.of(TARGET, 10L))).willReturn(List.of(user(TARGET, "대상"), left));

		// when
		List<BlockedUserResponse> list = service.list(ME);

		// then
		assertThat(list).containsExactly(new BlockedUserResponse(TARGET, "대상", null, NOW.atOffset(ZoneOffset.UTC)));
	}

	@Test
	void blockedUserHasProfileImageUrl() {
		// given
		String small = "https://m.example/a/ab12/s.webp";
		ProfileImageUrls urls = new ProfileImageUrls("https://m.example/a/ab12/l.webp", small);
		givenUsers();
		given(blocks.findByBlockerIdAndBlockedId(ME, TARGET)).willReturn(Optional.empty());
		given(avatars.of(List.of(TARGET))).willReturn(Map.of(TARGET, urls));
		given(blocks.findAllByBlockerIdOrderByCreatedAtDesc(ME)).willReturn(List.of(
				UserBlock.of(ME, TARGET, NOW), UserBlock.of(ME, 10L, NOW.minusSeconds(60))));
		given(users.findAllById(List.of(TARGET, 10L))).willReturn(List.of(user(TARGET, "대상"), user(10L, "다른")));
		given(avatars.of(List.of(TARGET, 10L))).willReturn(Map.of(TARGET, urls));

		// when / then
		assertThat(service.block(ME, TARGET).profileImageUrl()).isEqualTo(small);
		assertThat(service.list(ME)).extracting(BlockedUserResponse::profileImageUrl).containsExactly(small, null);
	}
}
