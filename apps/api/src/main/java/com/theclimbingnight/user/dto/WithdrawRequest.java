package com.theclimbingnight.user.dto;

/** 탈퇴 요청 본문. Apple로 가입한 사용자만 재인증으로 받은 authorization code를 싣는다. 본문 없이 보내도 된다. */
public record WithdrawRequest(String appleAuthorizationCode) {
}
