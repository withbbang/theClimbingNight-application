package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfilePurgeOriginalJobHandlerTest {

	@Mock OriginalStore originals;
	@Mock ProfileImageRepository images;
	@TempDir Path root;

	@Test
	void 원본을_지우고_원본_경로를_비운다() throws Exception {
		// given
		ProfileImage image = WorkerFixtures.profileImage(11L, ProfileImageStatus.REMOVED);
		image.recordOriginal("profile/2026/11/11.jpg");
		given(images.findById(11L)).willReturn(Optional.of(image));
		ProfilePurgeOriginalJobHandler handler = new ProfilePurgeOriginalJobHandler(originals, images,
				WorkerFixtures.tx());

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PURGE_ORIGINAL, 11L, 0));

		// then
		verify(originals).delete("profile/2026/11/11.jpg");
		assertThat(image.getOriginalPath()).isNull();
	}

	@Test
	void 파일이_이미_없어도_성공한다() throws Exception {
		// given: 실제 원본 폴더에 파일이 없다
		ProfileImage image = WorkerFixtures.profileImage(11L, ProfileImageStatus.REMOVED);
		image.recordOriginal("profile/2026/11/11.jpg");
		given(images.findById(11L)).willReturn(Optional.of(image));
		ProfilePurgeOriginalJobHandler handler = new ProfilePurgeOriginalJobHandler(new OriginalStore(root), images,
				WorkerFixtures.tx());

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PURGE_ORIGINAL, 11L, 0));

		// then
		assertThat(image.getOriginalPath()).isNull();
	}

	@Test
	void 원본_경로가_없으면_아무것도_안_한다() throws Exception {
		// given
		given(images.findById(11L))
				.willReturn(Optional.of(WorkerFixtures.profileImage(11L, ProfileImageStatus.REMOVED)));
		ProfilePurgeOriginalJobHandler handler = new ProfilePurgeOriginalJobHandler(originals, images,
				WorkerFixtures.tx());

		// when
		handler.handle(WorkerFixtures.profileJob(MediaJobKind.PURGE_ORIGINAL, 11L, 0));

		// then
		verifyNoInteractions(originals);
	}
}
