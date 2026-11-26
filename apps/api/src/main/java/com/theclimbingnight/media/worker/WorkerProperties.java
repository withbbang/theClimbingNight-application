package com.theclimbingnight.media.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 워커 설정. cachePurgeToken은 비밀값이라 toString에 넣지 않는다.
 * pollInterval은 작업이 없을 때 처음 쉬는 시간이고, 계속 없으면 idleMaxInterval까지 두 배씩 늘린다.
 */
@ConfigurationProperties("app.worker")
public record WorkerProperties(boolean enabled, Duration pollInterval, Duration idleMaxInterval,
		Duration ffmpegTimeout, String cloudflareZoneId, String cloudflareCachePurgeToken) {

	public WorkerProperties {
		pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
		idleMaxInterval = idleMaxInterval == null ? Duration.ofSeconds(30) : idleMaxInterval;
		ffmpegTimeout = ffmpegTimeout == null ? Duration.ofSeconds(30) : ffmpegTimeout;
	}

	public boolean cachePurgeConfigured() {
		return hasText(cloudflareZoneId) && hasText(cloudflareCachePurgeToken);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	public String toString() {
		return "WorkerProperties[enabled=" + enabled + ", pollInterval=" + pollInterval + ", idleMaxInterval="
				+ idleMaxInterval + ", ffmpegTimeout=" + ffmpegTimeout + ", cloudflareZoneId=" + cloudflareZoneId + "]";
	}
}
