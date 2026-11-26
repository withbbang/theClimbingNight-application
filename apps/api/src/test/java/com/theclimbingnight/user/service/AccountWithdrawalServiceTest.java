package com.theclimbingnight.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.social.AppleTokenRevoker;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountWithdrawalServiceTest {

	@Mock AuthIdentityRepository authIdentityRepository;
	@Mock AppleTokenRevoker revoker;
	@Mock UserService userService;

	private AccountWithdrawalService service;

	@BeforeEach
	void setUp() {
		service = new AccountWithdrawalService(authIdentityRepository, revoker, userService);
		given(revoker.enabled()).willReturn(true);
	}

	private void identity(SocialProvider provider, String providerUserId) {
		AuthIdentity identity = AuthIdentity.link(User.create("climber"), provider, providerUserId);
		given(authIdentityRepository.findFirstByUserId(7L)).willReturn(Optional.of(identity));
	}

	@Test
	void Apple_사용자가_code_없이_탈퇴하면_재인증을_요구하고_탈퇴하지_않는다() {
		// given
		identity(SocialProvider.APPLE, "apple-user");

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> service.withdraw(7L, null));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
		verify(userService, never()).withdraw(anyLong());
	}

	@Test
	void Apple_사용자가_빈_code를_보내도_재인증을_요구한다() {
		// given
		identity(SocialProvider.APPLE, "apple-user");

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> service.withdraw(7L, " "));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
		verify(revoker, never()).exchange(anyString(), anyString());
	}

	@Test
	void Apple_사용자는_교환_탈퇴_폐기_순서로_처리한다() {
		// given
		identity(SocialProvider.APPLE, "apple-user");
		given(revoker.exchange("c1", "apple-user")).willReturn("r1");

		// when
		service.withdraw(7L, "c1");

		// then
		InOrder order = inOrder(revoker, userService);
		order.verify(revoker).exchange("c1", "apple-user");
		order.verify(userService).withdraw(7L);
		order.verify(revoker).revoke("r1");
	}

	@Test
	void 교환이_실패하면_탈퇴하지_않는다() {
		// given
		identity(SocialProvider.APPLE, "apple-user");
		given(revoker.exchange(any(), any())).willThrow(new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE));

		// when
		BusinessException e = catchThrowableOfType(BusinessException.class, () -> service.withdraw(7L, "c1"));

		// then
		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		verify(userService, never()).withdraw(anyLong());
	}

	@Test
	void 키가_없으면_Apple_사용자도_code_없이_탈퇴한다() {
		// given
		identity(SocialProvider.APPLE, "apple-user");
		given(revoker.enabled()).willReturn(false);

		// when
		service.withdraw(7L, null);

		// then
		verify(userService).withdraw(7L);
		verify(revoker, never()).exchange(any(), any());
		verify(revoker, never()).revoke(any());
	}

	@Test
	void 카카오_사용자는_Apple을_부르지_않는다() {
		// given
		identity(SocialProvider.KAKAO, "kakao-user");

		// when
		service.withdraw(7L, "c1");

		// then
		verify(userService).withdraw(7L);
		verify(revoker, never()).exchange(any(), any());
		verify(revoker, never()).revoke(any());
	}

}
