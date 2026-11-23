package com.theclimbingnight.user.dto;

import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;

public record UserResponse(long id, String nickname, Role role) {

	private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

	public static UserResponse from(User user) {
		String nickname = user.getNickname() == null ? WITHDRAWN_NICKNAME : user.getNickname();
		return new UserResponse(user.getId(), nickname, user.getRole());
	}

}
