package com.theclimbingnight.media.worker;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileDeleteMediaJobHandlerTest {

	@Mock MediaStorage storage;
	@Mock CachePurger purger;
	@Mock ProfileImageRepository images;

	private ProfileDeleteMediaJobHandler handler;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		handler = new ProfileDeleteMediaJobHandler(storage, purger, urls, images, WorkerFixtures.tx());
	}

	@Test
	void 공개된_적_있는_사진은_공개_격리_업로드를_모두_지우고_캐시를_비운다() throws Exception {
		// given
		ProfileImage image = WorkerFixtures.profileImage(11L, ProfileImageStatus.REMOVED);
		image.recordOriginal("profile/2026/11/11.jpg");
		given(images.findById(11L)).willReturn(Optional.of(image));

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.DELETE_MEDIA, 11L, 0));

		// then
		verify(storage).deletePublic("a/k11/l.webp");
		verify(storage).deletePublic("a/k11/s.webp");
		verify(storage).deleteQuarantine("a/k11/l.webp");
		verify(storage).deleteQuarantine("a/k11/s.webp");
		verify(storage).deleteUpload("uploads/u11.jpg");
		verify(purger).purge(List.of("https://m.example/a/k11/l.webp", "https://m.example/a/k11/s.webp"));
	}

	@Test
	void 원본이_저장되기_전이면_캐시를_비우지_않는다() throws Exception {
		// given
		given(images.findById(11L))
				.willReturn(Optional.of(WorkerFixtures.profileImage(11L, ProfileImageStatus.REMOVED)));

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.DELETE_MEDIA, 11L, 0));

		// then
		verify(storage).deletePublic("a/k11/l.webp");
		verify(storage).deleteUpload("uploads/u11.jpg");
		verifyNoInteractions(purger);
	}

	@Test
	void 사진_행이_없으면_아무것도_안_한다() throws Exception {
		// given
		given(images.findById(11L)).willReturn(Optional.empty());

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.DELETE_MEDIA, 11L, 0));

		// then
		verifyNoInteractions(storage, purger);
	}
}
