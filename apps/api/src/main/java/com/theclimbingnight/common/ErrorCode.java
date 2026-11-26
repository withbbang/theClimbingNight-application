package com.theclimbingnight.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 항목을 찾을 수 없습니다."),
	COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	COMMON_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	AUTH_SIGNUP_EXPIRED(HttpStatus.BAD_REQUEST, "가입 시간이 지났습니다. 처음부터 다시 로그인해 주세요."),
	AUTH_INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요."),
	AUTH_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그인 서비스에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요."),
	AUTH_APPLE_REAUTH_REQUIRED(HttpStatus.BAD_REQUEST, "Apple 계정을 한 번 더 확인해야 탈퇴할 수 있어요."),
	AUTH_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다. 다시 로그인해 주세요."),
	USER_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다."),
	USER_NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	ROSTER_CSV_INVALID(HttpStatus.BAD_REQUEST, "CSV 형식이 올바르지 않습니다. 첫 줄에 '소모임닉네임' 머리글이 있어야 하고 따옴표가 올바르게 닫혀야 합니다."),
	ROSTER_CSV_ENCODING(HttpStatus.BAD_REQUEST, "CSV 파일을 UTF-8로 저장해 다시 올려 주세요."),
	ROSTER_CSV_TOO_MANY_ROWS(HttpStatus.BAD_REQUEST, "CSV는 한 번에 5,000행까지 올릴 수 있습니다."),
	MEMBERSHIP_ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 동호회원입니다."),
	MEMBERSHIP_REQUEST_PENDING(HttpStatus.CONFLICT, "이미 심사 중인 신청이 있습니다."),
	MEMBERSHIP_REQUEST_ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
	ROSTER_ALREADY_LINKED(HttpStatus.CONFLICT, "다른 회원과 연결된 명부 항목입니다."),
	MEMBERSHIP_LINKED_ELSEWHERE(HttpStatus.CONFLICT, "이 회원은 이미 다른 명부 항목에 연결되어 있습니다."),
	MEETUP_START_IN_PAST(HttpStatus.BAD_REQUEST, "시작 시각은 지금 이후여야 합니다."),
	MEETUP_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작한 벙입니다."),
	MEETUP_ENDED(HttpStatus.CONFLICT, "이미 끝난 벙입니다."),
	MEETUP_CANCELED(HttpStatus.CONFLICT, "취소된 벙입니다."),
	MEETUP_ROSTER_REQUIRED(HttpStatus.FORBIDDEN, "동호회원 명부와 연결된 계정만 신청할 수 있습니다."),
	MEETUP_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청한 벙입니다."),
	MEETUP_APPLICATION_REMOVED(HttpStatus.CONFLICT, "운영진이 내보낸 벙에는 다시 신청할 수 없습니다."),
	MEETUP_APPLICATION_NOT_ACTIVE(HttpStatus.CONFLICT, "이미 취소되었거나 내보낸 신청입니다."),
	PENALTY_NOT_LATE_CANCEL(HttpStatus.CONFLICT, "늦은 취소로 표시된 신청이 아닙니다."),
	PENALTY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 블랙리스트에 기록된 건입니다."),
	PENALTY_ALREADY_REVOKED(HttpStatus.CONFLICT, "이미 취소된 블랙리스트 기록입니다.");

	private final HttpStatus status;
	private final String message;

}
