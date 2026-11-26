package com.theclimbingnight.media.worker;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cloudflare 토큰이나 zone이 비었을 때 쓴다. 만들 때 경고를 한 번 남긴다. */
public class NoopCachePurger implements CachePurger {

	private static final Logger log = LoggerFactory.getLogger(NoopCachePurger.class);

	public NoopCachePurger() {
		log.warn("CLOUDFLARE_CACHE_PURGE_TOKEN 또는 CLOUDFLARE_ZONE_ID가 비어 있어 숨긴 사진의 CDN 캐시를 비우지 않습니다.");
	}

	@Override
	public void purge(List<String> urls) {
	}
}
