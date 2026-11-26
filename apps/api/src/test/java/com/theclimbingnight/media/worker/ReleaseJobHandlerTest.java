package com.theclimbingnight.media.worker;

import static com.theclimbingnight.media.worker.WorkerFixtures.NOW;
import static com.theclimbingnight.media.worker.WorkerFixtures.POST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReleaseJobHandlerTest {

	/** 난수를 모킹하면 nextBytes가 0으로 채운 그대로라 새 키가 정해진다. */
	private static final String NEW_KEY = "p/" + "0".repeat(32);
	private static final List<String> OLD_URLS = List.of("https://m.example/p/old/d.webp",
			"https://m.example/p/old/t.webp");
	private static final Instant FIRST_PUBLISHED = Instant.parse("2026-11-20T00:00:00Z");

	@Mock MediaStorage storage;
	@Mock OriginalStore originals;
	@Mock ImageTranscoder transcoder;
	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;
	@Mock CachePurger purger;

	private ReleaseJobHandler handler;
	private Post post;

	@BeforeEach
	void setUp() throws Exception {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		ProcessJobHandler process = new ProcessJobHandler(storage, originals, transcoder, posts, mediaRepository,
				WorkerFixtures.tx(), clock);
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		handler = new ReleaseJobHandler(storage, purger, urls, originals, process, posts, mediaRepository,
				WorkerFixtures.tx(), clock, mock(SecureRandom.class));
		post = WorkerFixtures.post(PostStatus.PENDING);
		ReflectionTestUtils.setField(post, "publishedAt", FIRST_PUBLISHED);
		lenient().when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
		lenient().when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
		lenient().when(transcoder.transcode(any(), any()))
				.thenAnswer(inv -> WorkerFixtures.transcoded(inv.getArgument(1, Path.class)));
	}

	private PostMedia givenMedia(PostMediaStatus status, String originalPath) {
		PostMedia media = WorkerFixtures.media(11L, status, "p/old");
		ReflectionTestUtils.setField(media, "originalPath", originalPath);
		given(mediaRepository.findById(11L)).willReturn(Optional.of(media));
		lenient().when(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(media));
		return media;
	}

	@Test
	void 새_키로_되돌리고_예전_격리본을_지운다() throws Exception {
		// given
		PostMedia media = givenMedia(PostMediaStatus.READY, "2026/11/11.jpg");
		given(storage.quarantineExists("p/old/d.webp")).willReturn(true);
		given(storage.quarantineExists("p/old/t.webp")).willReturn(true);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then
		verify(storage).copyQuarantineToPublic("p/old/d.webp", NEW_KEY + "/d.webp");
		verify(storage).copyQuarantineToPublic("p/old/t.webp", NEW_KEY + "/t.webp");
		verify(storage).deleteQuarantine("p/old/d.webp");
		verify(storage).deleteQuarantine("p/old/t.webp");
		verify(transcoder, never()).transcode(any(), any());
		assertThat(media.getPublicKey()).isEqualTo(NEW_KEY);
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(post.getPublishedAt()).isEqualTo(FIRST_PUBLISHED);
	}

	@Test
	void 격리본이_없으면_원본으로_변환한다() throws Exception {
		// given: 처리 중에 숨겨져 공개본도 격리본도 없다
		PostMedia media = givenMedia(PostMediaStatus.PROCESSING, "2026/11/11.jpg");
		given(originals.exists("2026/11/11.jpg")).willReturn(true);
		given(originals.open("2026/11/11.jpg")).willReturn(new ByteArrayInputStream(new byte[10]));

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then
		verify(storage).putPublic(eq(NEW_KEY + "/d.webp"), any(), eq("image/webp"));
		verify(storage).putPublic(eq(NEW_KEY + "/t.webp"), any(), eq("image/webp"));
		verify(originals, never()).save(org.mockito.ArgumentMatchers.anyLong(), any(), any());
		verify(storage, never()).openUpload(anyString());
		verify(storage).deletePublic("p/old/d.webp");
		verify(storage).deletePublic("p/old/t.webp");
		verify(purger).purge(OLD_URLS);
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.READY);
		assertThat(media.getPublicKey()).isEqualTo(NEW_KEY);
		assertThat(media.getOriginalPath()).isEqualTo("2026/11/11.jpg");
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
	}

	@Test
	void 원본도_없으면_업로드에서_처리한다() throws Exception {
		// given
		PostMedia media = givenMedia(PostMediaStatus.PROCESSING, null);
		given(storage.uploadedSize("uploads/u11.jpg")).willReturn(OptionalLong.of(10));
		given(storage.openUpload("uploads/u11.jpg")).willReturn(new ByteArrayInputStream(new byte[10]));
		given(originals.save(eq(11L), any(), eq(NOW))).willReturn("2026/11/11.jpg");

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then
		verify(storage).putPublic(eq(NEW_KEY + "/d.webp"), any(), eq("image/webp"));
		verify(storage).deleteUpload("uploads/u11.jpg");
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.READY);
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
	}

	@Test
	void 되살릴_사본이_하나도_없으면_사진과_게시물이_FAILED다() throws Exception {
		// given
		PostMedia media = givenMedia(PostMediaStatus.PROCESSING, null);
		given(storage.uploadedSize("uploads/u11.jpg")).willReturn(OptionalLong.empty());

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then
		verify(storage, never()).putPublic(anyString(), any(), anyString());
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.FAILED);
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
	}

	@Test
	void 그새_다시_숨긴_글이면_아무것도_안_한다() throws Exception {
		// given
		givenMedia(PostMediaStatus.READY, "2026/11/11.jpg");
		ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then
		org.mockito.Mockito.verifyNoInteractions(storage, originals, transcoder, purger);
	}

	@Test
	void 공개본을_되돌리는_사이에_숨겨지면_새_공개본을_지운다() throws Exception {
		// given
		PostMedia media = givenMedia(PostMediaStatus.READY, "2026/11/11.jpg");
		given(storage.quarantineExists(anyString())).willReturn(true);
		org.mockito.Mockito.doAnswer(inv -> {
			if (inv.getArgument(0).equals("p/old/t.webp")) {
				ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
			}
			return null;
		}).when(storage).copyQuarantineToPublic(anyString(), anyString());

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then
		verify(storage).deletePublic(NEW_KEY + "/d.webp");
		verify(storage).deletePublic(NEW_KEY + "/t.webp");
		verify(storage, never()).deleteQuarantine(anyString());
		assertThat(media.getPublicKey()).isEqualTo("p/old");
	}

	@Test
	void 격리본으로_되돌릴_때_남아_있던_예전_공개본도_지우고_캐시를_비운다() throws Exception {
		// given: 격리 작업이 복사는 마쳤지만 공개본을 지우기 전에 실패했다
		givenMedia(PostMediaStatus.READY, "2026/11/11.jpg");
		given(storage.quarantineExists(anyString())).willReturn(true);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0));

		// then: 새 사본을 만들기 전에 예전 공개본을 지운다
		InOrder order = inOrder(storage, purger);
		order.verify(storage).deletePublic("p/old/d.webp");
		order.verify(storage).deletePublic("p/old/t.webp");
		order.verify(purger).purge(OLD_URLS);
		order.verify(storage).copyQuarantineToPublic("p/old/d.webp", NEW_KEY + "/d.webp");
	}

	@Test
	void 되돌린_뒤_기록하다_실패하면_이번_새_사본을_지운다() throws Exception {
		// given
		PostMedia media = givenMedia(PostMediaStatus.READY, "2026/11/11.jpg");
		given(storage.quarantineExists(anyString())).willReturn(true);
		given(posts.findByIdForUpdate(POST_ID)).willThrow(new IllegalStateException("DB 끊김"));

		// when
		Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
				() -> handler.handle(WorkerFixtures.job(MediaJobKind.RELEASE, 11L, 0)));

		// then
		assertThat(thrown).hasMessage("DB 끊김");
		verify(storage).deletePublic(NEW_KEY + "/d.webp");
		verify(storage).deletePublic(NEW_KEY + "/t.webp");
		verify(storage, never()).deleteQuarantine(anyString());
		assertThat(media.getPublicKey()).isEqualTo("p/old");
	}
}
