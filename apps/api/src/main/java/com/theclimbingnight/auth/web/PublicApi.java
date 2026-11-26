package com.theclimbingnight.auth.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 토큰 없이 호출할 수 있는 API. Authorization 헤더가 없으면 비로그인(사용자 null)으로 통과하고, 유효한 토큰이면 사용자를
 * 채운다. 헤더가 있는데 유효하지 않으면(형식 오류·만료·없는/비활성 사용자) 401 AUTH_REQUIRED다.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
