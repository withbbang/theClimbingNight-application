package com.theclimbingnight.push.expo;

/** 429·5xx·연결 실패. 다시 시도해 볼 만하다. 메시지는 코드만 담는다. */
public class PushTransientException extends RuntimeException {

	private final String code;

	public PushTransientException(String code) {
		super(code, null, false, false);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
