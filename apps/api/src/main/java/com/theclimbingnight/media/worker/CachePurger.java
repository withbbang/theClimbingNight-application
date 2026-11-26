package com.theclimbingnight.media.worker;

import java.util.List;

/** 공개 URL의 CDN 캐시를 비운다. 실패하면 예외를 던져 작업 재시도로 넘긴다. */
public interface CachePurger {

	void purge(List<String> urls);
}
