package com.theclimbingnight.user.service;

import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.social.AppleTokenRevoker;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 요청의 진입점. Apple 사용자는 Apple 토큰을 바꾸고(탈퇴 전) 폐기한다(탈퇴 후).
 * Apple 호출은 사용자 행 잠금을 잡은 트랜잭션 밖에서 한다. 그래서 이 클래스에는 트랜잭션이 없다.
 */
@Service
public class AccountWithdrawalService {

	private final AuthIdentityRepository authIdentityRepository;
	private final AppleTokenRevoker appleTokenRevoker;
	private final UserService userService;

	public AccountWithdrawalService(AuthIdentityRepository authIdentityRepository, AppleTokenRevoker appleTokenRevoker,
			UserService userService) {
		this.authIdentityRepository = authIdentityRepository;
		this.appleTokenRevoker = appleTokenRevoker;
		this.userService = userService;
	}

	public void withdraw(long userId, String appleAuthorizationCode) {
		String appleRefreshToken = null;
		AuthIdentity identity = authIdentityRepository.findFirstByUserId(userId).orElse(null);
		if (identity != null && identity.getProvider() == SocialProvider.APPLE && appleTokenRevoker.enabled()) {
			if (appleAuthorizationCode == null || appleAuthorizationCode.isBlank()) {
				throw new BusinessException(ErrorCode.AUTH_APPLE_REAUTH_REQUIRED);
			}
			appleRefreshToken = appleTokenRevoker.exchange(appleAuthorizationCode, identity.getProviderUserId());
		}
		userService.withdraw(userId);
		if (appleRefreshToken != null) {
			appleTokenRevoker.revoke(appleRefreshToken);
		}
	}

}
