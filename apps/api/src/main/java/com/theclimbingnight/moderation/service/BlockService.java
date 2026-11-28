package com.theclimbingnight.moderation.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.moderation.dto.BlockedUserResponse;
import com.theclimbingnight.moderation.entity.UserBlock;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BlockService {

	private final UserBlockRepository blocks;
	private final UserRepository users;
	private final UserAvatars avatars;
	private final Clock clock;

	public BlockService(UserBlockRepository blocks, UserRepository users, UserAvatars avatars,
			Clock clock) {
		this.blocks = blocks;
		this.users = users;
		this.avatars = avatars;
		this.clock = clock;
	}

	/**
	 * 이미 차단했으면 처음 차단을 그대로 돌려준다. 차단하는 사람 행을 잠가, 같은 사람의 동시 요청이 같은 키를 두 번 넣지 않게 한다.
	 */
	@Transactional
	public BlockedUserResponse block(long blockerId, long userId) {
		if (blockerId == userId) {
			throw new BusinessException(ErrorCode.BLOCK_SELF);
		}
		users.findByIdForUpdate(blockerId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		User target = users.findById(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		UserBlock block = blocks.findByBlockerIdAndBlockedId(blockerId, userId).orElse(null);
		if (block == null) {
			block = UserBlock.of(blockerId, userId, clock.instant());
			blocks.save(block);
		}
		return new BlockedUserResponse(userId, target.getNickname(),
				UserAvatars.smallUrl(avatars.of(List.of(userId)), userId), block.getCreatedAt().atOffset(ZoneOffset.UTC));
	}

	@Transactional
	public void unblock(long blockerId, long userId) {
		blocks.deleteBlock(blockerId, userId);
	}

	/** 탈퇴한 상대는 뺀다. 차단 행은 남겨 둔다. */
	public List<BlockedUserResponse> list(long blockerId) {
		List<UserBlock> mine = blocks.findAllByBlockerIdOrderByCreatedAtDesc(blockerId);
		if (mine.isEmpty()) {
			return List.of();
		}
		Map<Long, User> byId = users.findAllById(mine.stream().map(UserBlock::getBlockedId).toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		List<UserBlock> shown = mine.stream().filter(b -> {
			User user = byId.get(b.getBlockedId());
			return user != null && user.isActive();
		}).toList();
		Map<Long, ProfileImageUrls> images = avatars.of(shown.stream().map(UserBlock::getBlockedId).toList());
		return shown.stream().map(b -> new BlockedUserResponse(b.getBlockedId(), byId.get(b.getBlockedId()).getNickname(),
				UserAvatars.smallUrl(images, b.getBlockedId()), b.getCreatedAt().atOffset(ZoneOffset.UTC))).toList();
	}
}
