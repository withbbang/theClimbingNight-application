package com.theclimbingnight.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 항목을 찾을 수 없습니다."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	AUTH_SIGNUP_EXPIRED(HttpStatus.BAD_REQUEST, "가입 시간이 지났습니다. 처음부터 다시 로그인해 주세요."),
	AUTH_INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요."),
	AUTH_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그인 서비스에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

}
