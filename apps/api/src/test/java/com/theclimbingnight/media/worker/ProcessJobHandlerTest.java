package com.theclimbingnight.media.worker;

import static com.theclimbingnight.media.worker.WorkerFixtures.NOW;
import static com.theclimbingnight.media.worker.WorkerFixtures.POST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProcessJobHandlerTest {

	private static final String ORIGINAL = "2026/11/11.jpg";

	@Mock MediaStorage storage;
	@Mock OriginalStore originals;
	@Mock ImageTranscoder transcoder;
	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;

	private ProcessJobHandler handler;
	private Post post;
	private PostMedia media;

	@BeforeEach
	void setUp() throws Exception {
		handler = new ProcessJobHandler(storage, originals, transcoder, posts, mediaRepository, WorkerFixtures.tx(),
				Clock.fixed(NOW, ZoneOffset.UTC));
		post = WorkerFixtures.post(PostStatus.PENDING);
		media = WorkerFixtures.media(11L, PostMediaStatus.PROCESSING, "p/k11");
		lenient().when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
		lenient().when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
		lenient().when(mediaRepository.findById(11L)).thenReturn(Optional.of(media));
		lenient().when(storage.openUpload("uploads/u11.jpg")).thenReturn(new ByteArrayInputStream(new byte[10]));
		lenient().when(originals.save(eq(11L), any(), eq(NOW))).thenReturn(ORIGINAL);
		lenient().when(transcoder.transcode(any(), any()))
				.thenAnswer(inv -> WorkerFixtures.transcoded(inv.getArgument(1, Path.class)));
	}

	@Test
	void 모든_사진이_READY면_게시물이_VISIBLE이_된다() throws Exception {
		// given
		given(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).willReturn(List.of(media));

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 11L, 0));

		// then
		verify(storage).putPublic(eq("p/k11/d.webp"), any(), eq("image/webp"));
		verify(storage).putPublic(eq("p/k11/t.webp"), any(), eq("image/webp"));
		verify(storage).deleteUpload("uploads/u11.jpg");
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.READY);
		assertThat(media.getOriginalPath()).isEqualTo(ORIGINAL);
		assertThat(media.getWidth()).isEqualTo(1080);
		assertThat(media.getHeight()).isEqualTo(810);
		assertThat(media.getByteSize()).isEqualTo(1234L);
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(post.getPublishedAt()).isEqualTo(NOW);
	}

	@Test
	void 하나라도_남으면_PENDING_그대로다() throws Exception {
		// given
		PostMedia other = WorkerFixtures.media(12L, PostMediaStatus.PROCESSING, "p/k12");
		given(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).willReturn(List.of(media, other));

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 11L, 0));

		// then
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.READY);
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING);
		assertThat(post.getPublishedAt()).isNull();
	}

	@Test
	void 업로드_뒤_게시물이_숨겨졌으면_방금_올린_공개본을_지운다() throws Exception {
		// given: 두 번째 사본을 올리는 사이에 운영진이 숨긴다
		lenient().doAnswer(inv -> {
			if (inv.getArgument(0).equals("p/k11/t.webp")) {
				ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN_PENDING);
			}
			return null;
		}).when(storage).putPublic(anyString(), any(), anyString());

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 11L, 0));

		// then
		verify(storage).deletePublic("p/k11/d.webp");
		verify(storage).deletePublic("p/k11/t.webp");
		verify(storage, never()).deleteUpload(anyString());
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN_PENDING);
	}

	@Test
	void 변환_전에_숨겨졌으면_올리지_않는다() throws Exception {
		// given: 변환하는 사이에 숨긴다
		org.mockito.BDDMockito.willAnswer(inv -> {
			ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
			return WorkerFixtures.transcoded(inv.getArgument(1, Path.class));
		}).given(transcoder).transcode(any(), any());

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 11L, 0));

		// then
		verify(storage, never()).putPublic(anyString(), any(), anyString());
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
		assertThat(media.getOriginalPath()).isEqualTo(ORIGINAL);
	}

	@Test
	void 지운_글이면_받지도_않는다() throws Exception {
		// given
		post.softDelete(NOW);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 11L, 0));

		// then
		verifyNoInteractions(storage, originals, transcoder);
	}

	@Test
	void 사진_행이_없으면_아무것도_안_한다() throws Exception {
		// given
		given(mediaRepository.findById(anyLong())).willReturn(Optional.empty());

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 99L, 0));

		// then
		verifyNoInteractions(storage, originals, transcoder);
	}

	@Test
	void 공개본을_올리다_실패하면_올린_사본을_지우고_예외를_그대로_던진다() throws Exception {
		// given
		lenient().doAnswer(inv -> {
			if (inv.getArgument(0).equals("p/k11/t.webp")) {
				throw new IllegalStateException("R2 503");
			}
			return null;
		}).when(storage).putPublic(anyString(), any(), anyString());

		// when
		Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
				() -> handler.handle(WorkerFixtures.job(MediaJobKind.PROCESS, 11L, 0)));

		// then
		assertThat(thrown).hasMessage("R2 503");
		verify(storage).deletePublic("p/k11/d.webp");
		verify(storage).deletePublic("p/k11/t.webp");
		verify(storage, never()).deleteUpload(anyString());
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
	}
}
