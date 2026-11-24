package com.theclimbingnight.push.expo;

import java.util.Map;

/** to는 푸시 토큰이라 toString에서 뺀다. */
public record PushMessage(String to, String title, String body, Map<String, Object> data) {

	@Override
	public String toString() {
		return "PushMessage[title=" + title + "]";
	}
}
