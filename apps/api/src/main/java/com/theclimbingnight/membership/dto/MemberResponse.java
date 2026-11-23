package com.theclimbingnight.membership.dto;

import com.theclimbingnight.user.entity.Role;

/** 동호회원 목록 한 줄. 명부에 연결되지 않은 회원(예: 명부 없는 ADMIN)은 rosterMemberId·somoimNickname이 null이다. */
public record MemberResponse(long userId, String nickname, Role role, Long rosterMemberId, String somoimNickname) {
}
