package com.theclimbingnight.auth.service;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.entity.RefreshToken;
import com.theclimbingnight.auth.repository.AuthIdentityRepository;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.auth.social.SocialIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.auth.social.SocialTokenVerifiers;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.NicknamePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private static final String CONSTRAINT_NICKNAME = "uk_users_nickname";
	private static final String CONSTRAINT_IDENTITY = "uk_auth_identities_provider";

	private final UserRepository userRepository;
	private final AuthIdentityRepository authIdentityRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final SocialTokenVerifiers socialTokenVerifiers;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokens refreshTokens;
	private final NicknamePolicy nicknamePolicy;
	private final AuthProperties properties;
	private final Clock clock;

	public AuthService(UserRepository userRepository, AuthIdentityRepository authIdentityRepository,
			RefreshTokenRepository refreshTokenRepository, SocialTokenVerifiers socialTokenVerifiers,
			JwtTokenService jwtTokenService, RefreshTokens refreshTokens, NicknamePolicy nicknamePolicy,
			AuthProperties properties, Clock clock) {
		this.userRepository = userRepository;
		this.authIdentityRepository = authIdentityRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.socialTokenVerifiers = socialTokenVerifiers;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokens = refreshTokens;
		this.nicknamePolicy = nicknamePolicy;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public LoginResult login(SocialProvider provider, String socialToken) {
		SocialIdentity identity = socialTokenVerifiers.get(provider).verify(socialToken);
		return authIdentityRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
				.<LoginResult>map(linked -> {
					User user = linked.getUser();
					if (!user.isActive()) {
						throw new BusinessException(ErrorCode.AUTH_REQUIRED);
					}
					return new LoginResult.LoggedIn(issueTokens(user));
				})
				.orElseGet(() -> new LoginResult.SignupRequired(jwtTokenService.issueSignupToken(identity)));
	}

	@Transactional
	public TokenPair signup(String signupToken, String rawNickname) {
		SocialIdentity identity = jwtTokenService.parseSignupToken(signupToken);
		String nickname = nicknamePolicy.normalize(rawNickname);
		if (findIdentity(identity).isPresent()) {
			throw new BusinessException(ErrorCode.AUTH_ALREADY_REGISTERED);
		}
		// MySQL의 기본 콜레이션은 대소문자를 구분하지 않으므로 서비스에서도 같은 기준으로 먼저 확인한다.
		if (userRepository.existsByNicknameIgnoreCase(nickname)) {
			throw new BusinessException(ErrorCode.USER_NICKNAME_TAKEN);
		}
		User user;
		try {
			user = userRepository.saveAndFlush(User.create(nickname));
			authIdentityRepository.saveAndFlush(
					AuthIdentity.link(user, identity.provider(), identity.providerUserId()));
		} catch (DataIntegrityViolationException e) {
			// 동시 가입. 실패한 세션·스냅샷으로 재조회하지 않고 위반된 제약 이름만으로 판단한다.
			throw toBusinessException(e);
		}
		return issueTokens(user);
	}

	@Transactional
	public TokenPair refresh(String refreshToken) {
		Instant now = clock.instant();
		RefreshToken stored = refreshTokenRepository.findByTokenHash(RefreshTokens.hash(refreshToken))
				.filter(token -> token.isUsable(now))
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		User user = userRepository.findById(stored.getUserId())
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		stored.revoke(now);
		return issueTokens(user);
	}

	@Transactional
	public void logout(String refreshToken) {
		refreshTokenRepository.findByTokenHash(RefreshTokens.hash(refreshToken))
				.ifPresent(token -> token.revoke(clock.instant()));
	}

	@Transactional
	public TokenPair issueTokens(User user) {
		String accessToken = jwtTokenService.issueAccessToken(user.getId());
		Instant accessExpiresAt = jwtTokenService.accessTokenExpiresAt();
		String refreshToken = refreshTokens.newToken();
		refreshTokenRepository.save(RefreshToken.issue(user.getId(), RefreshTokens.hash(refreshToken),
				clock.instant().plus(properties.refreshTtl())));
		return new TokenPair(accessToken, accessExpiresAt, refreshToken, user);
	}

	private static RuntimeException toBusinessException(DataIntegrityViolationException e) {
		String violated = violatedConstraints(e);
		if (violated.contains(CONSTRAINT_IDENTITY)) {
			return new BusinessException(ErrorCode.AUTH_ALREADY_REGISTERED);
		}
		if (violated.contains(CONSTRAINT_NICKNAME)) {
			return new BusinessException(ErrorCode.USER_NICKNAME_TAKEN);
		}
		return e;
	}

	/** 원인 사슬의 제약 이름과 메시지를 소문자로 이어 붙인다. (DB마다 이름이 메시지에만 실리기도 한다.) */
	private static String violatedConstraints(Throwable e) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
				sb.append(cve.getConstraintName()).append(' ');
			}
			if (t.getMessage() != null) {
				sb.append(t.getMessage()).append(' ');
			}
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	private Optional<AuthIdentity> findIdentity(SocialIdentity identity) {
		return authIdentityRepository.findByProviderAndProviderUserId(identity.provider(),
				identity.providerUserId());
	}

}
