package com.theclimbingnight.push.expo;

/** 다시 보내도 소용없는 요청 오류. 메시지는 코드만 담고 토큰이나 응답 본문은 담지 않는다. */
public class PushRequestException extends RuntimeException {

	private final String code;

	public PushRequestException(String code) {
		super(code, null, false, false);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
