package com.theclimbingnight.push.controller;

import com.theclimbingnight.auth.web.AuthenticatedUser;
import com.theclimbingnight.auth.web.CurrentUser;
import com.theclimbingnight.auth.web.RequireRole;
import com.theclimbingnight.push.dto.PushTokenRegisterRequest;
import com.theclimbingnight.push.service.PushTokenService;
import com.theclimbingnight.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-tokens")
public class PushTokenController {

	private final PushTokenService pushTokenService;

	public PushTokenController(PushTokenService pushTokenService) {
		this.pushTokenService = pushTokenService;
	}

	@RequireRole(Role.USER)
	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void register(@CurrentUser AuthenticatedUser user, @Valid @RequestBody PushTokenRegisterRequest request) {
		pushTokenService.register(user.id(), request.expoPushToken(), request.platform());
	}
}
