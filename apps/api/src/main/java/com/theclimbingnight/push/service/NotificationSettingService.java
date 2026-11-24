package com.theclimbingnight.push.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.push.dto.NotificationSettingRequest;
import com.theclimbingnight.push.dto.NotificationSettingResponse;
import com.theclimbingnight.push.entity.NotificationSetting;
import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationSettingService {

	private final UserRepository userRepository;
	private final NotificationSettingRepository notificationSettingRepository;

	public NotificationSettingService(UserRepository userRepository,
			NotificationSettingRepository notificationSettingRepository) {
		this.userRepository = userRepository;
		this.notificationSettingRepository = notificationSettingRepository;
	}

	/** 행이 없으면 모두 켜진 것으로 본다. */
	public NotificationSettingResponse getMine(long userId) {
		return notificationSettingRepository.findById(userId)
				.map(s -> new NotificationSettingResponse(s.isNewMeetup(), s.isNewNotice()))
				.orElseGet(() -> new NotificationSettingResponse(true, true));
	}

	@Transactional
	public NotificationSettingResponse update(long userId, NotificationSettingRequest request) {
		// 사용자 행을 잠가 같은 사용자의 동시 요청을 직렬화한다 (설정 행 PK 충돌 방지).
		userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		boolean newMeetup = request.newMeetup();
		boolean newNotice = request.newNotice();
		notificationSettingRepository.findById(userId).ifPresentOrElse(
				existing -> existing.change(newMeetup, newNotice),
				() -> notificationSettingRepository
						.saveAndFlush(NotificationSetting.create(userId, newMeetup, newNotice)));
		return new NotificationSettingResponse(newMeetup, newNotice);
	}
}
