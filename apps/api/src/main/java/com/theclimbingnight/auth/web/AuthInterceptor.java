package com.theclimbingnight.auth.web;

import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

	static final String ATTRIBUTE = AuthenticatedUser.class.getName();
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenService jwtTokenService;
	private final UserRepository userRepository;

	public AuthInterceptor(JwtTokenService jwtTokenService, UserRepository userRepository) {
		this.jwtTokenService = jwtTokenService;
		this.userRepository = userRepository;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		RequireRole requireRole = findAnnotation(handlerMethod, RequireRole.class);
		// @RequireRole은 @PublicApi보다 우선한다: 역할이 필요하면 항상 유효한 사용자를 요구한다.
		boolean anonymousAllowed = findAnnotation(handlerMethod, PublicApi.class) != null && requireRole == null;
		AuthenticatedUser user;
		try {
			user = authenticate(request);
		} catch (BusinessException e) {
			if (anonymousAllowed) {
				return true;
			}
			throw e;
		}
		if (requireRole != null && !user.role().atLeast(requireRole.value())) {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}
		request.setAttribute(ATTRIBUTE, user);
		return true;
	}

	/** 헤더가 없거나 Bearer가 아니거나 토큰·사용자가 유효하지 않으면 AUTH_REQUIRED. 성공하면 항상 non-null. */
	private AuthenticatedUser authenticate(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}
		long userId = jwtTokenService.parseAccessToken(header.substring(BEARER_PREFIX.length()).trim());
		User user = userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		return new AuthenticatedUser(user.getId(), user.getRole());
	}

	private static <A extends java.lang.annotation.Annotation> A findAnnotation(HandlerMethod handlerMethod,
			Class<A> type) {
		A onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), type);
		return onMethod != null ? onMethod
				: AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), type);
	}

}
