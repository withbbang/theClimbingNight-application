package com.theclimbingnight.media.job;

/** 작은 priority가 먼저 실행된다. */
public enum MediaJobKind {
	QUARANTINE(0), DELETE_MEDIA(0), RELEASE(1), PROCESS(2), PURGE_ORIGINAL(3);

	private final int priority;

	MediaJobKind(int priority) {
		this.priority = priority;
	}

	public int priority() {
		return priority;
	}
}
