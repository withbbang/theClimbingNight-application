package com.theclimbingnight.push.service;

import java.time.Duration;

/** 재시도 사이 대기. 테스트에서 실제로 기다리지 않게 분리했다. */
public interface Pause {

	void sleep(Duration duration);
}
