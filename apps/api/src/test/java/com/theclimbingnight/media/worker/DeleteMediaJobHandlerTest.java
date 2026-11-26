package com.theclimbingnight.media.worker;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteMediaJobHandlerTest {

	@Mock MediaStorage storage;
	@Mock CachePurger purger;
	@Mock PostMediaRepository mediaRepository;

	private DeleteMediaJobHandler handler;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		handler = new DeleteMediaJobHandler(storage, purger, urls, mediaRepository, WorkerFixtures.tx());
	}

	@Test
	void 공개_격리_업로드_객체를_모두_지우고_캐시를_비운다() throws Exception {
		// given
		given(mediaRepository.findById(11L))
				.willReturn(Optional.of(WorkerFixtures.media(11L, PostMediaStatus.READY, "p/k11")));

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.DELETE_MEDIA, 11L, 0));

		// then
		verify(storage).deletePublic("p/k11/d.webp");
		verify(storage).deletePublic("p/k11/t.webp");
		verify(storage).deleteQuarantine("p/k11/d.webp");
		verify(storage).deleteQuarantine("p/k11/t.webp");
		verify(storage).deleteUpload("uploads/u11.jpg");
		verify(purger).purge(List.of("https://m.example/p/k11/d.webp", "https://m.example/p/k11/t.webp"));
	}

	@Test
	void 공개된_적_없는_사진은_캐시를_비우지_않는다() throws Exception {
		// given: 아직 올리는 중인 초안 사진
		given(mediaRepository.findById(11L))
				.willReturn(Optional.of(WorkerFixtures.media(11L, PostMediaStatus.UPLOADING, "p/k11")));

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.DELETE_MEDIA, 11L, 0));

		// then
		verify(storage).deleteUpload("uploads/u11.jpg");
		verifyNoInteractions(purger);
	}

	@Test
	void 사진_행이_없으면_아무것도_안_한다() throws Exception {
		// given
		given(mediaRepository.findById(anyLong())).willReturn(Optional.empty());

		// when
		handler.handle(WorkerFixtures.job(MediaJobKind.DELETE_MEDIA, 11L, 0));

		// then
		verifyNoInteractions(storage, purger);
	}
}
