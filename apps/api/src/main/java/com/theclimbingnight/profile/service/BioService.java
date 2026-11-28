package com.theclimbingnight.profile.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.profile.dto.ProfileResponse;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BioService {

	private final UserRepository users;
	private final PostRepository posts;
	private final BioPolicy policy;
	private final UserAvatars avatars;

	public BioService(UserRepository users, PostRepository posts, BioPolicy policy, UserAvatars avatars) {
		this.users = users;
		this.posts = posts;
		this.policy = policy;
		this.avatars = avatars;
	}

	@Transactional
	public ProfileResponse update(long userId, String rawBio) {
		String bio = policy.normalize(rawBio);
		User user = users.findByIdForUpdate(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		user.changeBio(bio);
		return new ProfileResponse(user.getId(), user.getNickname(), user.getBio(),
				avatars.of(List.of(userId)).get(userId),
				posts.countByAuthorIdAndStatusAndDeletedAtIsNull(userId, PostStatus.VISIBLE), true);
	}
}
