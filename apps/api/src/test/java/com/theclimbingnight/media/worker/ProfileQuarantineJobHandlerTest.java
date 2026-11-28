package com.theclimbingnight.media.worker;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileQuarantineJobHandlerTest {

	@Mock MediaStorage storage;
	@Mock CachePurger purger;
	@Mock ProfileImageRepository images;

	private ProfileQuarantineJobHandler handler;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		handler = new ProfileQuarantineJobHandler(storage, purger, urls, images, WorkerFixtures.tx());
	}

	private void givenImage(ProfileImageStatus status) {
		given(images.findById(11L)).willReturn(Optional.of(WorkerFixtures.profileImage(11L, status)));
	}

	@Test
	void 내린_사진의_공개본을_격리로_옮기고_두_URL의_캐시를_비운다() throws Exception {
		// given
		givenImage(ProfileImageStatus.REMOVED);
		given(storage.publicExists("a/k11/l.webp")).willReturn(true);
		given(storage.publicExists("a/k11/s.webp")).willReturn(true);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.QUARANTINE, 11L, 0));

		// then
		InOrder order = inOrder(storage, purger);
		order.verify(storage).copyPublicToQuarantine("a/k11/l.webp");
		order.verify(storage).copyPublicToQuarantine("a/k11/s.webp");
		order.verify(storage).deletePublic("a/k11/l.webp");
		order.verify(storage).deletePublic("a/k11/s.webp");
		order.verify(purger).purge(List.of("https://m.example/a/k11/l.webp", "https://m.example/a/k11/s.webp"));
	}

	@Test
	void 격리본이_이미_있으면_다시_복사하지_않고_공개본만_지운다() throws Exception {
		// given: 지난 시도에서 복사까지 하고 실패했다
		givenImage(ProfileImageStatus.REMOVED);
		given(storage.quarantineExists("a/k11/l.webp")).willReturn(true);
		given(storage.quarantineExists("a/k11/s.webp")).willReturn(true);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.QUARANTINE, 11L, 1));

		// then
		verify(storage, never()).copyPublicToQuarantine(anyString());
		verify(storage).deletePublic("a/k11/l.webp");
		verify(storage).deletePublic("a/k11/s.webp");
		verify(purger).purge(List.of("https://m.example/a/k11/l.webp", "https://m.example/a/k11/s.webp"));
	}

	@Test
	void 공개본도_격리본도_없으면_아무것도_안_한다() throws Exception {
		// given: 공개되기 전에 내린 사진
		givenImage(ProfileImageStatus.REMOVED);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.QUARANTINE, 11L, 0));

		// then
		verify(storage, never()).copyPublicToQuarantine(anyString());
		verify(storage, never()).deletePublic(anyString());
		verifyNoInteractions(purger);
	}

	@Test
	void 내린_사진이_아니면_아무것도_안_한다() throws Exception {
		// given
		givenImage(ProfileImageStatus.READY);

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.QUARANTINE, 11L, 0));

		// then
		verifyNoInteractions(storage, purger);
	}
}
