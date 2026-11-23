package com.theclimbingnight.user.entity;

public enum Role {
	USER, MEMBER, ADMIN;

	/** 선언 순서로 비교해 이 역할이 required 이상인지 판단한다. */
	public boolean atLeast(Role required) {
		return this.compareTo(required) >= 0;
	}
}
