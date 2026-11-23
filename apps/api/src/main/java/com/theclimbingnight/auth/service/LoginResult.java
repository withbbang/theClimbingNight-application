package com.theclimbingnight.auth.service;

public sealed interface LoginResult permits LoginResult.LoggedIn, LoginResult.SignupRequired {

	record LoggedIn(TokenPair tokens) implements LoginResult {
	}

	record SignupRequired(String signupToken) implements LoginResult {
	}

}
