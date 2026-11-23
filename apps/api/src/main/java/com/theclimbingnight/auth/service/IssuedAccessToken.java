package com.theclimbingnight.auth.service;

import java.time.Instant;

/** 발급한 액세스 토큰과, JWT exp 클레임과 같은 값(초 단위)의 만료 시각. */
public record IssuedAccessToken(String token, Instant expiresAt) {
}
