package com.theclimbingnight.media;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * accessKeyId·secretAccessKey는 비밀값이다. toString에 넣지 않는다.
 * enabled는 설정값을 쓰지 않고, R2 키 다섯 개와 공개 주소(publicBaseUrl)가 모두 있을 때만 true로 판정한다.
 * 공개 주소가 비면 응답의 사진 URL이 상대경로가 되어 모두 깨지므로 같이 본다.
 */
@ConfigurationProperties("app.media")
public record MediaProperties(boolean enabled, String accountId, String accessKeyId, String secretAccessKey,
		String incomingBucket, String publicBucket, String publicBaseUrl, Path originalsDir) {

	public MediaProperties {
		enabled = hasText(accountId) && hasText(accessKeyId) && hasText(secretAccessKey)
				&& hasText(incomingBucket) && hasText(publicBucket) && hasText(publicBaseUrl);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	public String toString() {
		return "MediaProperties[enabled=" + enabled + ", incomingBucket=" + incomingBucket
				+ ", publicBucket=" + publicBucket + ", publicBaseUrl=" + publicBaseUrl + "]";
	}
}
