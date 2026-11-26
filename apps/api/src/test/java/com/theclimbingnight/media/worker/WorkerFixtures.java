package com.theclimbingnight.media.worker;

import static org.mockito.Mockito.mock;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 워커 단위 테스트가 같이 쓰는 엔티티·변환 결과 준비. */
final class WorkerFixtures {

	static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
	static final long POST_ID = 50L;

	private WorkerFixtures() {
	}

	/** 트랜잭션 관리자는 모킹한다. 콜백만 그대로 실행된다. */
	static TransactionTemplate tx() {
		return new TransactionTemplate(mock(PlatformTransactionManager.class));
	}

	static Post post(PostStatus status) {
		Post post = Post.draft(7L);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	static PostMedia media(long id, PostMediaStatus status, String publicKey) {
		PostMedia media = PostMedia.image(POST_ID, (int) id, "uploads/u" + id + ".jpg", publicKey);
		ReflectionTestUtils.setField(media, "id", id);
		ReflectionTestUtils.setField(media, "status", status);
		return media;
	}

	static MediaJob job(MediaJobKind kind, long targetId, int attempts) {
		MediaJob job = MediaJob.queue(kind, targetId, NOW);
		ReflectionTestUtils.setField(job, "id", 900L);
		ReflectionTestUtils.setField(job, "attempts", attempts);
		return job;
	}

	/** 변환기가 workDir에 실제 파일 두 개를 만든 것처럼 한다. */
	static TranscodedImage transcoded(Path workDir) {
		try {
			Path display = Files.write(workDir.resolve("d.webp"), new byte[1234]);
			Path thumb = Files.write(workDir.resolve("t.webp"), new byte[99]);
			return new TranscodedImage(display, thumb, 1080, 810);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
