package com.theclimbingnight.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.media.MediaConfig;
import com.theclimbingnight.media.MediaProperties;

class R2MediaStorageTest {

	private final MediaProperties properties = new MediaProperties(true, "acc", "AKIAEXAMPLE", "secret-example",
			"media-in", "media-pub", "https://media.x", null);

	/** presigner는 서명만 계산하고 네트워크를 쓰지 않는다. S3Client는 이 테스트에서 호출하지 않는다. */
	private R2MediaStorage storage() {
		return new R2MediaStorage(MediaConfig.s3Client(properties), MediaConfig.s3Presigner(properties),
				"media-in", "media-pub");
	}

	@Test
	void presign_URL은_비공개_버킷과_키_만료_서명_길이를_담는다() {
		Instant before = Instant.now();
		PresignedUpload upload = storage().presignUpload("uploads/abc.jpg", "image/jpeg", 12345L,
				Duration.ofMinutes(15));

		assertThat(upload.url())
				.startsWith("https://acc.r2.cloudflarestorage.com/media-in/uploads/abc.jpg?")
				.contains("X-Amz-Expires=900")
				.contains("X-Amz-Signature=");
		assertThat(upload.url()).containsIgnoringCase("content-length");
		assertThat(upload.headers()).containsEntry("Content-Type", "image/jpeg")
				.containsEntry("Content-Length", "12345")
				.doesNotContainKey("Host");
		assertThat(upload.expiresAt()).isBetween(before.plus(Duration.ofMinutes(14)), before.plus(Duration.ofMinutes(16)));
	}

	@Test
	void 격리본_조회_URL은_quarantine_접두사_키를_가리킨다() {
		var uri = storage().presignQuarantineGet("p/u/d.webp", Duration.ofMinutes(5));

		assertThat(uri.toString())
				.startsWith("https://acc.r2.cloudflarestorage.com/media-in/quarantine/p/u/d.webp?")
				.contains("X-Amz-Expires=300");
	}

	@Test
	void 비활성_저장소는_모든_호출이_MEDIA_UNAVAILABLE이다() {
		DisabledMediaStorage disabled = new DisabledMediaStorage();

		assertThatThrownBy(() -> disabled.presignUpload("k", "image/jpeg", 1, Duration.ofMinutes(1)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNAVAILABLE));
		assertThatThrownBy(() -> disabled.deletePublic("k")).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> disabled.uploadedSize("k")).isInstanceOf(BusinessException.class);
	}
}
