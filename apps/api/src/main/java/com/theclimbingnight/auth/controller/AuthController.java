package com.theclimbingnight.auth.controller;

import com.theclimbingnight.auth.dto.LoginRequest;
import com.theclimbingnight.auth.dto.LoginResponse;
import com.theclimbingnight.auth.dto.LogoutRequest;
import com.theclimbingnight.auth.dto.RefreshRequest;
import com.theclimbingnight.auth.dto.SignupRequest;
import com.theclimbingnight.auth.dto.TokenResponse;
import com.theclimbingnight.auth.service.AuthService;
import com.theclimbingnight.auth.web.PublicApi;
import com.theclimbingnight.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PublicApi
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(LoginResponse.from(authService.login(request.provider(), request.token())));
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success(
				TokenResponse.from(authService.signup(request.signupToken(), request.nickname())));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.success(TokenResponse.from(authService.refresh(request.refreshToken())));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken(), request.expoPushToken());
	}

}
