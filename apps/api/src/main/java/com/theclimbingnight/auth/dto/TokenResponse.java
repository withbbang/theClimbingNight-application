package com.theclimbingnight.auth.dto;

import com.theclimbingnight.auth.service.TokenPair;
import com.theclimbingnight.user.dto.UserResponse;
import java.time.Instant;

public record TokenResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
		UserResponse user) {

	public static TokenResponse from(TokenPair pair) {
		return new TokenResponse(pair.accessToken(), pair.accessTokenExpiresAt(), pair.refreshToken(),
				UserResponse.from(pair.user()));
	}

}
