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
import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.push.dto.PushTokenRegisterRequest;
import com.theclimbingnight.push.service.PushTokenService;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.NicknamePolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private static final Pattern EXPO_PUSH_TOKEN = Pattern.compile(PushTokenRegisterRequest.EXPO_PUSH_TOKEN_PATTERN);
	private static final String CONSTRAINT_NICKNAME = "uk_users_nickname";
	/** 폐기 직후 응답이 유실돼 앱이 재시도하는 경우를 살리는 유예. 이 안의 재사용은 전체 폐기하지 않는다. */
	private static final Duration REUSE_GRACE = Duration.ofSeconds(30);
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
	private final TransactionTemplate transactionTemplate;
	private final PushTokenService pushTokenService;

	public AuthService(UserRepository userRepository, AuthIdentityRepository authIdentityRepository,
			RefreshTokenRepository refreshTokenRepository, SocialTokenVerifiers socialTokenVerifiers,
			JwtTokenService jwtTokenService, RefreshTokens refreshTokens, NicknamePolicy nicknamePolicy,
			AuthProperties properties, Clock clock, TransactionTemplate transactionTemplate,
			PushTokenService pushTokenService) {
		this.userRepository = userRepository;
		this.authIdentityRepository = authIdentityRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.socialTokenVerifiers = socialTokenVerifiers;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokens = refreshTokens;
		this.nicknamePolicy = nicknamePolicy;
		this.properties = properties;
		this.clock = clock;
		this.transactionTemplate = transactionTemplate;
		this.pushTokenService = pushTokenService;
	}

	/**
	 * 외부 HTTP(소셜 검증)는 DB 트랜잭션 밖에서 호출한다. 클래스의 readOnly 트랜잭션도 이 메서드에서는 빠지고,
	 * DB 조회와 토큰 발급만 TransactionTemplate으로 묶는다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public LoginResult login(SocialProvider provider, String socialToken) {
		SocialIdentity identity = socialTokenVerifiers.get(provider).verify(socialToken);
		return transactionTemplate.execute(status ->
				authIdentityRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
						.<LoginResult>map(linked -> {
							// 탈퇴와 겹치면 탈퇴가 폐기한 뒤에 토큰이 생길 수 있으므로 사용자를 잠그고 확인한다.
							// 잠그기 전에 user를 읽으면 낡은 상태가 영속성 컨텍스트에 남으므로 ID만 쓴다.
							User user = userRepository.findByIdForUpdate(linked.getUser().getId())
									.filter(User::isActive)
									.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
							return new LoginResult.LoggedIn(issueTokens(user));
						})
						.orElseGet(() -> new LoginResult.SignupRequired(jwtTokenService.issueSignupToken(identity))));
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

	/**
	 * 재사용 감지로 전체 폐기를 한 뒤 BusinessException(401)을 던지므로, 그 예외로는 롤백하지 않는다.
	 * noRollbackFor가 없으면 예외 때문에 트랜잭션이 롤백돼 전체 폐기가 사라진다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public TokenPair refresh(String refreshToken) {
		Instant now = clock.instant();
		RefreshToken stored = refreshTokenRepository.findByTokenHash(RefreshTokens.hash(refreshToken))
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		if (stored.getRevokedAt() != null) {
			// 이미 폐기된 토큰이 다시 왔다. 만료 전이고 유예가 지났다면 탈취·재사용으로 보고 모두 폐기한다.
			boolean withinGrace = now.isBefore(stored.getRevokedAt().plus(REUSE_GRACE));
			if (!withinGrace && now.isBefore(stored.getExpiresAt())) {
				// 갱신·로그인이 아직 커밋하지 않은 새 토큰까지 폐기하도록 끝나기를 기다린다.
				userRepository.findByIdForUpdate(stored.getUserId());
				// 대기 전 시각으로 폐기하면 재사용 유예가 그만큼 줄어든다.
				refreshTokenRepository.revokeAllActiveByUserId(stored.getUserId(), clock.instant());
			}
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}
		if (!stored.isUsable(now)) {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}
		// 탈퇴(사용자 → 리프레시 토큰)와 같은 잠금 순서. 새 토큰 INSERT의 FK 공유 잠금이 폐기 뒤에 걸리면 교착할 수 있다.
		User user = userRepository.findByIdForUpdate(stored.getUserId())
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		// 잠금을 기다린 만큼 시각이 흘렀을 수 있다.
		Instant lockedAt = clock.instant();
		if (!stored.isUsable(lockedAt)) {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}
		// 조회 뒤 다른 요청이 먼저 폐기했을 수 있으므로 조건부 UPDATE로 한 요청만 통과시킨다.
		// 동시 경합은 재사용 공격이 아니므로 전체 폐기 없이 401만 준다.
		if (refreshTokenRepository.revokeIfActive(stored.getId(), lockedAt) == 0) {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}
		return issue(user, hash -> stored.rotate(hash, clock.instant().plus(properties.refreshTtl())));
	}

	@Transactional
	public void logout(String refreshToken, String expoPushToken) {
		String tokenHash = RefreshTokens.hash(refreshToken);
		refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
			// 탈퇴·갱신과 같은 잠금 순서(사용자 → 리프레시 토큰 → 푸시 토큰). 갱신 중이면 끝나기를 기다린다.
			userRepository.findByIdForUpdate(token.getUserId());
			Instant now = clock.instant();
			// 앱이 새 토큰을 받기 전에 옛 토큰으로 로그아웃했다면, 그 로그인에서 갱신된 토큰을 폐기한다.
			boolean ended = refreshTokenRepository.revokeIfActive(token.getId(), now) > 0
					|| refreshTokenRepository.revokeFamily(token.rootId(), now) > 0;
			// 한 기기에서 A가 로그아웃하고 B가 로그인할 수 있으므로, 리프레시 토큰 소유자의 토큰만 지운다.
			if (expoPushToken != null && EXPO_PUSH_TOKEN.matcher(expoPushToken).matches()) {
				if (!ended) {
					// 응답이 유실돼 늦게 온 재시도다. 그 사이 같은 토큰을 다시 등록했다면 지우지 않는다.
					// 잠금을 기다리는 사이 폐기됐을 수 있으므로 폐기 시각을 다시 읽는다.
					refreshTokenRepository.findByTokenHash(tokenHash).map(RefreshToken::getRevokedAt)
							.ifPresent(revokedAt -> pushTokenService.deleteOwnedIfNotSeenSince(
									token.getUserId(), expoPushToken, revokedAt));
					return;
				}
				pushTokenService.deleteOwned(token.getUserId(), expoPushToken);
			}
		});
	}

	@Transactional
	public TokenPair issueTokens(User user) {
		return issue(user, hash -> RefreshToken.issue(user.getId(), hash,
				clock.instant().plus(properties.refreshTtl())));
	}

	private TokenPair issue(User user, Function<String, RefreshToken> toEntity) {
		IssuedAccessToken access = jwtTokenService.issueAccessToken(user.getId());
		String refreshToken = refreshTokens.newToken();
		refreshTokenRepository.save(toEntity.apply(RefreshTokens.hash(refreshToken)));
		return new TokenPair(access.token(), access.expiresAt(), refreshToken, user);
	}

	private static RuntimeException toBusinessException(DataIntegrityViolationException e) {
		String violated = ConstraintViolations.violated(e);
		if (violated.contains(CONSTRAINT_IDENTITY)) {
			return new BusinessException(ErrorCode.AUTH_ALREADY_REGISTERED);
		}
		if (violated.contains(CONSTRAINT_NICKNAME)) {
			return new BusinessException(ErrorCode.USER_NICKNAME_TAKEN);
		}
		return e;
	}

	private Optional<AuthIdentity> findIdentity(SocialIdentity identity) {
		return authIdentityRepository.findByProviderAndProviderUserId(identity.provider(),
				identity.providerUserId());
	}

}
