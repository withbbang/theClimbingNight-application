package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.push.dto.NotificationSettingRequest;
import com.theclimbingnight.push.dto.NotificationSettingResponse;
import com.theclimbingnight.push.entity.NotificationSetting;
import com.theclimbingnight.push.repository.NotificationSettingRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

	@Mock UserRepository userRepository;
	@Mock NotificationSettingRepository notificationSettingRepository;
	@InjectMocks NotificationSettingService service;

	private User user(long id) {
		User user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void 설정이_없으면_모두_켜짐이다() {
		// given
		given(notificationSettingRepository.findById(3L)).willReturn(Optional.empty());

		// when
		NotificationSettingResponse response = service.getMine(3L);

		// then
		assertThat(response).isEqualTo(new NotificationSettingResponse(true, true));
	}

	@Test
	void 설정을_바꾸면_저장된다() {
		// given
		NotificationSetting existing = NotificationSetting.create(3L, true, true);
		given(userRepository.findByIdForUpdate(3L)).willReturn(Optional.of(user(3L)));
		given(notificationSettingRepository.findById(3L)).willReturn(Optional.of(existing));

		// when
		NotificationSettingResponse response = service.update(3L, new NotificationSettingRequest(false, true));

		// then
		assertThat(response).isEqualTo(new NotificationSettingResponse(false, true));
		assertThat(existing.isNewMeetup()).isFalse();
		assertThat(existing.isNewNotice()).isTrue();
	}

	@Test
	void 설정이_없을_때_바꾸면_사용자_잠금_뒤_새로_만든다() {
		// given
		given(userRepository.findByIdForUpdate(3L)).willReturn(Optional.of(user(3L)));
		given(notificationSettingRepository.findById(3L)).willReturn(Optional.empty());

		// when
		NotificationSettingResponse response = service.update(3L, new NotificationSettingRequest(true, false));

		// then
		assertThat(response).isEqualTo(new NotificationSettingResponse(true, false));
		InOrder order = inOrder(userRepository, notificationSettingRepository);
		order.verify(userRepository).findByIdForUpdate(3L);
		ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
		order.verify(notificationSettingRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo(3L);
		assertThat(captor.getValue().isNewNotice()).isFalse();
	}

	@Test
	void 탈퇴한_사용자는_설정을_바꿀_수_없다() {
		// given
		User left = user(3L);
		left.withdraw(java.time.Instant.parse("2026-06-01T00:00:00Z"));
		given(userRepository.findByIdForUpdate(3L)).willReturn(Optional.of(left));

		// when, then
		assertThatThrownBy(() -> service.update(3L, new NotificationSettingRequest(true, true)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(notificationSettingRepository, org.mockito.Mockito.never()).saveAndFlush(any());
	}
}
