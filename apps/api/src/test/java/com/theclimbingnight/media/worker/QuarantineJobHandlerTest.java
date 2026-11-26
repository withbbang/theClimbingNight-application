package com.theclimbingnight.media.worker;

import static com.theclimbingnight.media.worker.WorkerFixtures.POST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuarantineJobHandlerTest {

	@Mock MediaStorage storage;
	@Mock CachePurger purger;
	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;

	private QuarantineJobHandler handler;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		handler = new QuarantineJobHandler(storage, purger, urls, posts, mediaRepository, WorkerFixtures.tx());
	}

	private PostMedia givenTarget(PostStatus postStatus, PostMediaStatus mediaStatus) {
		PostMedia media = WorkerFixtures.media(11L, mediaStatus, "p/k11");
		given(mediaRepository.findById(11L)).willReturn(Optional.of(media));
		given(posts.findById(POST_ID)).willReturn(Optional.of(WorkerFixtures.post(postStatus)));
		return media;
	}

	@Test
	void 공개본을_격리로_옮기고_두_URL의_캐시를_비운다() throws Exception {
		// given
		givenTarget(PostStatus.HIDDEN_PENDING, PostMediaStatus.READY);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.QUARANTINE, 11L, 0));

		// then
		InOrder order = inOrder(storage, purger);
		order.verify(storage).copyPublicToQuarantine("p/k11/d.webp");
		order.verify(storage).copyPublicToQuarantine("p/k11/t.webp");
		order.verify(storage).deletePublic("p/k11/d.webp");
		order.verify(storage).deletePublic("p/k11/t.webp");
		order.verify(purger).purge(List.of("https://m.example/p/k11/d.webp", "https://m.example/p/k11/t.webp"));
	}

	@Test
	void 격리본이_이미_있으면_다시_복사하지_않고_공개본만_지운다() throws Exception {
		// given: 지난 시도에서 복사까지 하고 실패했다
		givenTarget(PostStatus.HIDDEN, PostMediaStatus.READY);
		given(storage.quarantineExists("p/k11/d.webp")).willReturn(true);
		given(storage.quarantineExists("p/k11/t.webp")).willReturn(true);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.QUARANTINE, 11L, 1));

		// then
		verify(storage, never()).copyPublicToQuarantine(anyString());
		verify(storage).deletePublic("p/k11/d.webp");
		verify(storage).deletePublic("p/k11/t.webp");
	}

	@Test
	void 처리_전에_숨긴_사진이면_공개본이_없으니_아무것도_안_한다() throws Exception {
		// given
		givenTarget(PostStatus.HIDDEN_PENDING, PostMediaStatus.PROCESSING);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.QUARANTINE, 11L, 0));

		// then
		verify(storage, never()).copyPublicToQuarantine(anyString());
		verify(storage, never()).deletePublic(anyString());
		verifyNoInteractions(purger);
	}

	@Test
	void 복원하고_공개_작업_전에_다시_숨기면_남은_예전_공개본을_격리한다() throws Exception {
		// given: 숨긴 뒤 격리 작업이 돌기 전에 복원했다(사진은 PROCESSING으로 돌아감). RELEASE 전에 다시 숨겼다
		PostMedia media = givenTarget(PostStatus.HIDDEN_PENDING, PostMediaStatus.READY);
		com.theclimbingnight.feed.entity.Post post = WorkerFixtures.post(PostStatus.HIDDEN_PENDING);
		given(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).willReturn(List.of(media));
		new com.theclimbingnight.media.job.MediaJobScheduler(
				org.mockito.Mockito.mock(com.theclimbingnight.media.job.MediaJobRepository.class), mediaRepository,
				java.time.Clock.systemUTC()).onPostRestored(post);
		given(storage.publicExists(anyString())).willReturn(true);

		// when: 첫 숨김과 두 번째 숨김의 격리 작업이 차례로 돈다
		handler.handle(WorkerFixtures.job(MediaJobKind.QUARANTINE, 11L, 0));
		given(storage.publicExists(anyString())).willReturn(false);
		handler.handle(WorkerFixtures.job(MediaJobKind.QUARANTINE, 11L, 0));

		// then: 한 번만 복사하고, 예전 공개본을 지우고 캐시를 비운다
		assertThat(media.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
		verify(storage).copyPublicToQuarantine("p/k11/d.webp");
		verify(storage).copyPublicToQuarantine("p/k11/t.webp");
		verify(storage).deletePublic("p/k11/d.webp");
		verify(storage).deletePublic("p/k11/t.webp");
		verify(purger).purge(List.of("https://m.example/p/k11/d.webp", "https://m.example/p/k11/t.webp"));
	}

	@Test
	void 그새_복원된_글이면_아무것도_안_한다() throws Exception {
		// given
		givenTarget(PostStatus.PENDING, PostMediaStatus.READY);

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.QUARANTINE, 11L, 0));

		// then
		verifyNoInteractions(storage, purger);
	}
}
