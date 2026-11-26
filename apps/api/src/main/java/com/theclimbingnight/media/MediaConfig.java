package com.theclimbingnight.media;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.theclimbingnight.media.storage.DisabledMediaStorage;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.R2MediaStorage;

@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfig {

	private static final Region REGION = Region.of("auto");

	/** 키 다섯 개와 공개 주소가 모두 있으면 R2, 하나라도 비면 모든 호출을 MEDIA_UNAVAILABLE로 거절한다. */
	@Bean
	MediaStorage mediaStorage(MediaProperties properties) {
		if (!properties.enabled()) {
			return new DisabledMediaStorage();
		}
		return new R2MediaStorage(s3Client(properties), s3Presigner(properties),
				properties.incomingBucket(), properties.publicBucket());
	}

	public static S3Client s3Client(MediaProperties properties) {
		return S3Client.builder()
				.endpointOverride(endpoint(properties))
				.region(REGION)
				.credentialsProvider(credentials(properties))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				// R2는 SDK 기본 체크섬 헤더를 모두 받지 않는다. 필요할 때만 계산하게 한다.
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.httpClientBuilder(UrlConnectionHttpClient.builder()
						.connectionTimeout(Duration.ofSeconds(5))
						.socketTimeout(Duration.ofSeconds(60)))
				.build();
	}

	public static S3Presigner s3Presigner(MediaProperties properties) {
		return S3Presigner.builder()
				.endpointOverride(endpoint(properties))
				.region(REGION)
				.credentialsProvider(credentials(properties))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
	}

	private static URI endpoint(MediaProperties properties) {
		return URI.create("https://" + properties.accountId() + ".r2.cloudflarestorage.com");
	}

	private static StaticCredentialsProvider credentials(MediaProperties properties) {
		return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
	}
}
