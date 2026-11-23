package com.theclimbingnight.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclimbingnight.auth.service.LoginResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(String status, TokenResponse tokens, String signupToken) {

	public static LoginResponse from(LoginResult result) {
		if (result instanceof LoginResult.LoggedIn loggedIn) {
			return new LoginResponse("LOGGED_IN", TokenResponse.from(loggedIn.tokens()), null);
		}
		return new LoginResponse("SIGNUP_REQUIRED", null, ((LoginResult.SignupRequired) result).signupToken());
	}

}
