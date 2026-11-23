package com.theclimbingnight.auth.service;

import com.theclimbingnight.user.entity.User;
import java.time.Instant;

public record TokenPair(String accessToken, Instant accessTokenExpiresAt, String refreshToken, User user) {
}
