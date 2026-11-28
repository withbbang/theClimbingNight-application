package com.theclimbingnight.user.service;

import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 여러 사용자의 지금 프로필 사진 주소를 한 번에 모은다. */
@Component
public class UserAvatars {

	private final ProfileImageRepository images;
	private final MediaUrls urls;

	public UserAvatars(ProfileImageRepository images, MediaUrls urls) {
		this.images = images;
		this.urls = urls;
	}

	/** READY인 지금 사진만 담는다. 사진이 없는 사용자는 키가 없다. */
	@Transactional(readOnly = true)
	public Map<Long, ProfileImageUrls> of(Collection<Long> userIds) {
		Map<Long, ProfileImageUrls> result = new HashMap<>();
		if (userIds.isEmpty()) {
			return result;
		}
		for (ProfileImageRepository.CurrentImage row : images.findCurrentReady(userIds)) {
			result.put(row.userId(), new ProfileImageUrls(urls.publicUrl(row.publicKey() + ProfileImage.LARGE_SUFFIX),
					urls.publicUrl(row.publicKey() + ProfileImage.SMALL_SUFFIX)));
		}
		return result;
	}

	/** 작은 사진 주소. 없으면 null. */
	public static String smallUrl(Map<Long, ProfileImageUrls> avatars, long userId) {
		ProfileImageUrls urls = avatars.get(userId);
		return urls == null ? null : urls.smallUrl();
	}
}
