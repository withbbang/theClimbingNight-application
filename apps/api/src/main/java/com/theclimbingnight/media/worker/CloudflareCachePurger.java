package com.theclimbingnight.media.worker;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cloudflare 캐시 비우기(purge by URL). 2xx가 아니면 예외를 던져 작업 재시도로 넘긴다.
 * 예외에는 상태 코드만 담고 응답 본문·토큰·원본 예외는 담지 않는다.
 */
public class CloudflareCachePurger implements CachePurger {

	static final String BASE_URL = "https://api.cloudflare.com/client/v4";

	private final RestClient restClient;
	private final String zoneId;
	private final String token;

	public CloudflareCachePurger(RestClient restClient, String zoneId, String token) {
		this.restClient = restClient;
		this.zoneId = zoneId;
		this.token = token;
	}

	@Override
	public void purge(List<String> urls) {
		if (urls.isEmpty()) {
			return;
		}
		try {
			restClient.post()
					.uri(BASE_URL + "/zones/{zone}/purge_cache", zoneId)
					.headers(headers -> headers.setBearerAuth(token))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("files", urls))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("Cloudflare 캐시 비우기 실패 HTTP " + e.getStatusCode().value());
		} catch (RuntimeException e) {
			throw new IllegalStateException("Cloudflare 캐시 비우기 실패 " + e.getClass().getSimpleName());
		}
	}
}
