package com.theclimbingnight.moderation.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.moderation.dto.BlockedUserResponse;
import com.theclimbingnight.moderation.entity.UserBlock;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
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
	private final Clock clock;

	public BlockService(UserBlockRepository blocks, UserRepository users, Clock clock) {
		this.blocks = blocks;
		this.users = users;
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
		return new BlockedUserResponse(userId, target.getNickname(), block.getCreatedAt().atOffset(ZoneOffset.UTC));
	}

	@Transactional
	public void unblock(long blockerId, long userId) {
		blocks.deleteBlock(blockerId, userId);
	}

	public List<BlockedUserResponse> list(long blockerId) {
		List<UserBlock> mine = blocks.findAllByBlockerIdOrderByCreatedAtDesc(blockerId);
		if (mine.isEmpty()) {
			return List.of();
		}
		Map<Long, User> byId = users.findAllById(mine.stream().map(UserBlock::getBlockedId).toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return mine.stream().map(b -> {
			User user = byId.get(b.getBlockedId());
			return new BlockedUserResponse(b.getBlockedId(), user == null ? null : user.getNickname(),
					b.getCreatedAt().atOffset(ZoneOffset.UTC));
		}).toList();
	}
}
