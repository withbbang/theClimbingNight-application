package com.theclimbingnight.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.dto.PostCreateRequest;
import com.theclimbingnight.feed.dto.PostDraftResponse;
import com.theclimbingnight.feed.dto.PostMediaSpec;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.DisabledMediaStorage;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.PresignedUpload;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
	private static final long AUTHOR = 7L;

	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;
	@Mock UserRepository users;
	@Mock MediaStorage storage;
	@Mock MediaJobRepository jobs;
	@Mock MediaJobScheduler scheduler;

	private final MutableClock clock = new MutableClock(NOW);
	private PostCommandService service;

	@BeforeEach
	void setUp() {
		service = newService(storage, scheduler);
	}

	private PostCommandService newService(MediaStorage mediaStorage, MediaJobScheduler mediaJobScheduler) {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		return new PostCommandService(posts, mediaRepository, users, mediaStorage, mediaJobScheduler,
				new PostResponseMapper(urls), new SecureRandom(), clock);
	}

	private Post post(long id, long authorId, PostStatus status) {
		Post post = Post.draft(authorId);
		ReflectionTestUtils.setField(post, "id", id);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	private PostMedia media(long id, long postId, int order) {
		PostMedia media = PostMedia.image(postId, order, "uploads/u" + id + ".jpg", "p/k" + id);
		ReflectionTestUtils.setField(media, "id", id);
		return media;
	}

	private void givenAuthor() {
		User user = User.create("작성자");
		ReflectionTestUtils.setField(user, "id", AUTHOR);
		given(users.findById(AUTHOR)).willReturn(Optional.of(user));
	}

	@Test
	void 사진_수만큼_초안_행과_업로드_주소를_만든다() {
		// given
		given(posts.save(any(Post.class))).willAnswer(inv -> {
			Post saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 50L);
			return saved;
		});
		given(mediaRepository.saveAll(any())).willAnswer(inv -> {
			List<PostMedia> saved = inv.getArgument(0);
			long id = 100;
			for (PostMedia m : saved) {
				ReflectionTestUtils.setField(m, "id", id++);
			}
			return saved;
		});
		given(storage.presignUpload(anyString(), eq("image/jpeg"), eq(1000L), eq(Duration.ofMinutes(15))))
				.willReturn(new PresignedUpload("https://r2/put", Map.of("Content-Type", "image/jpeg"),
						NOW.plus(Duration.ofMinutes(15))));
		PostCreateRequest request = new PostCreateRequest(
				List.of(new PostMediaSpec("image/jpeg", 1000L, 800, 600), new PostMediaSpec("image/jpeg", 1000L, 800, 600)));

		// when
		PostDraftResponse response = service.createDraft(AUTHOR, request);

		// then
		assertThat(response.postId()).isEqualTo(50L);
		assertThat(response.uploads()).extracting("mediaId").containsExactly(100L, 101L);
		assertThat(response.uploads().get(0).url()).isEqualTo("https://r2/put");
		ArgumentCaptor<List<PostMedia>> saved = ArgumentCaptor.forClass(List.class);
		verify(mediaRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(PostMedia::getSortOrder).containsExactly(0, 1);
		assertThat(saved.getValue().get(0).getUploadKey()).matches("uploads/[0-9a-f]{32}\\.jpg");
		assertThat(saved.getValue().get(0).getPublicKey()).matches("p/[0-9a-f]{32}");
		assertThat(saved.getValue().get(0).getPublicKey()).isNotEqualTo(saved.getValue().get(1).getPublicKey());
		assertThat(saved.getValue().get(0).getUploadKey()).doesNotContain(
				saved.getValue().get(0).getPublicKey().substring(2));
	}

	@Test
	void 미디어가_꺼져_있으면_초안_만들기는_MEDIA_UNAVAILABLE이다() {
		// given
		PostCommandService disabled = newService(new DisabledMediaStorage(), scheduler);
		given(posts.save(any(Post.class))).willAnswer(inv -> {
			Post saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 50L);
			return saved;
		});
		given(mediaRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
		PostCreateRequest request = new PostCreateRequest(List.of(new PostMediaSpec("image/jpeg", 1000L, 8, 6)));

		// when / then
		assertThatThrownBy(() -> disabled.createDraft(AUTHOR, request))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNAVAILABLE));
	}

	@Test
	void 초안이_아니면_게시는_POST_NOT_DRAFT다() {
		// given
		given(posts.findByIdForUpdate(1L)).willReturn(Optional.of(post(1L, AUTHOR, PostStatus.PENDING)));

		// when / then
		assertThatThrownBy(() -> service.publish(AUTHOR, 1L, "본문"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_DRAFT));
		verifyNoInteractions(scheduler);
	}

	@Test
	void 올라가지_않은_사진이_있으면_POST_UPLOAD_INCOMPLETE다() {
		// given
		Post draft = post(1L, AUTHOR, PostStatus.DRAFT);
		PostMedia a = media(10, 1L, 0);
		PostMedia b = media(11, 1L, 1);
		ReflectionTestUtils.setField(a, "byteSize", 1000L);
		ReflectionTestUtils.setField(b, "byteSize", 1000L);
		given(posts.findByIdForUpdate(1L)).willReturn(Optional.of(draft));
		given(mediaRepository.findAllByPostIdOrderBySortOrder(1L)).willReturn(List.of(a, b));
		given(storage.uploadedSize(a.getUploadKey())).willReturn(OptionalLong.of(1000L));
		given(storage.uploadedSize(b.getUploadKey())).willReturn(OptionalLong.empty());

		// when / then
		assertThatThrownBy(() -> service.publish(AUTHOR, 1L, "본문"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_UPLOAD_INCOMPLETE));
		assertThat(draft.getStatus()).isEqualTo(PostStatus.DRAFT);
		verifyNoInteractions(scheduler);
	}

	@Test
	void 선언과_크기가_다르면_POST_UPLOAD_INCOMPLETE다() {
		// given
		Post draft = post(1L, AUTHOR, PostStatus.DRAFT);
		PostMedia a = media(10, 1L, 0);
		ReflectionTestUtils.setField(a, "byteSize", 1000L);
		given(posts.findByIdForUpdate(1L)).willReturn(Optional.of(draft));
		given(mediaRepository.findAllByPostIdOrderBySortOrder(1L)).willReturn(List.of(a));
		given(storage.uploadedSize(a.getUploadKey())).willReturn(OptionalLong.of(999L));

		// when / then
		assertThatThrownBy(() -> service.publish(AUTHOR, 1L, "본문"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_UPLOAD_INCOMPLETE));
	}

	@Test
	void 게시하면_사진마다_PROCESS_작업이_생기고_상태는_PENDING이다() {
		// given
		Post draft = post(1L, AUTHOR, PostStatus.DRAFT);
		PostMedia a = media(10, 1L, 0);
		PostMedia b = media(11, 1L, 1);
		ReflectionTestUtils.setField(a, "byteSize", 1000L);
		ReflectionTestUtils.setField(b, "byteSize", 2000L);
		given(posts.findByIdForUpdate(1L)).willReturn(Optional.of(draft));
		given(mediaRepository.findAllByPostIdOrderBySortOrder(1L)).willReturn(List.of(a, b));
		given(storage.uploadedSize(a.getUploadKey())).willReturn(OptionalLong.of(1000L));
		given(storage.uploadedSize(b.getUploadKey())).willReturn(OptionalLong.of(2000L));
		givenAuthor();

		// when
		PostResponse response = service.publish(AUTHOR, 1L, "오늘의 완등");

		// then
		assertThat(draft.getStatus()).isEqualTo(PostStatus.PENDING);
		assertThat(draft.getBody()).isEqualTo("오늘의 완등");
		assertThat(a.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
		assertThat(b.getStatus()).isEqualTo(PostMediaStatus.PROCESSING);
		verify(scheduler).enqueueProcess(List.of(a, b));
		assertThat(response.status()).isEqualTo("PENDING");
		assertThat(response.mine()).isTrue();
		assertThat(response.media()).hasSize(2);
		assertThat(response.media().get(0).url()).isNull();
	}

	@Test
	void 남의_게시물_게시는_404다() {
		// given
		given(posts.findByIdForUpdate(1L)).willReturn(Optional.of(post(1L, 99L, PostStatus.DRAFT)));

		// when / then
		assertThatThrownBy(() -> service.publish(AUTHOR, 1L, "본문"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 지워진_게시물은_작성자도_404다() {
		// given
		Post deleted = post(1L, AUTHOR, PostStatus.DRAFT);
		deleted.softDelete(NOW);
		given(posts.findByIdForUpdate(1L)).willReturn(Optional.of(deleted));

		// when / then
		assertThatThrownBy(() -> service.publish(AUTHOR, 1L, "본문"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 남의_게시물_수정은_404다() {
		// given
		given(posts.findById(1L)).willReturn(Optional.of(post(1L, 99L, PostStatus.VISIBLE)));

		// when / then
		assertThatThrownBy(() -> service.update(AUTHOR, 1L, "새 본문"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 숨겨진_글은_수정할_수_없다() {
		// given
		given(posts.findById(1L)).willReturn(Optional.of(post(1L, AUTHOR, PostStatus.HIDDEN_PENDING)));
		given(posts.findById(2L)).willReturn(Optional.of(post(2L, AUTHOR, PostStatus.HIDDEN)));

		// when / then
		for (long id : new long[] {1L, 2L}) {
			assertThatThrownBy(() -> service.update(AUTHOR, id, "새 본문"))
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_EDITABLE));
		}
	}

	@Test
	void 작성자는_본문을_고친다() {
		// given
		Post visible = post(1L, AUTHOR, PostStatus.VISIBLE);
		given(posts.findById(1L)).willReturn(Optional.of(visible));
		given(mediaRepository.findAllByPostIdOrderBySortOrder(1L)).willReturn(List.of());
		givenAuthor();

		// when
		PostResponse response = service.update(AUTHOR, 1L, "새 본문");

		// then
		assertThat(visible.getBody()).isEqualTo("새 본문");
		assertThat(response.body()).isEqualTo("새 본문");
	}

	@Test
	void ADMIN은_남의_게시물을_지울_수_있다() {
		// given
		Post other = post(1L, 99L, PostStatus.VISIBLE);
		given(posts.findById(1L)).willReturn(Optional.of(other));

		// when
		service.delete(AUTHOR, Role.ADMIN, 1L);

		// then
		assertThat(other.getDeletedAt()).isEqualTo(NOW);
		verify(scheduler).onPostRemoved(other);
	}

	@Test
	void 일반_회원은_남의_게시물을_못_지운다() {
		// given
		given(posts.findById(1L)).willReturn(Optional.of(post(1L, 99L, PostStatus.VISIBLE)));

		// when / then
		assertThatThrownBy(() -> service.delete(AUTHOR, Role.MEMBER, 1L))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		verify(scheduler, never()).onPostRemoved(any());
	}

	@Test
	void 삭제하면_대기중_PROCESS를_취소하고_DELETE_MEDIA와_30일_뒤_PURGE_ORIGINAL을_만든다() {
		// given
		MediaJobScheduler real = new MediaJobScheduler(jobs, mediaRepository, clock);
		PostCommandService withRealScheduler = newService(storage, real);
		Post mine = post(1L, AUTHOR, PostStatus.VISIBLE);
		PostMedia a = media(10, 1L, 0);
		PostMedia b = media(11, 1L, 1);
		given(posts.findById(1L)).willReturn(Optional.of(mine));
		given(mediaRepository.findAllByPostIdOrderBySortOrder(1L)).willReturn(List.of(a, b));

		// when
		withRealScheduler.delete(AUTHOR, Role.USER, 1L);

		// then
		verify(jobs).cancelQueued(anyCollection(), eq(MediaJobKind.PROCESS));
		ArgumentCaptor<List<MediaJob>> saved = ArgumentCaptor.forClass(List.class);
		verify(jobs).saveAll(saved.capture());
		List<MediaJob> created = saved.getValue();
		assertThat(created).hasSize(4);
		assertThat(created).filteredOn(j -> j.getKind() == MediaJobKind.DELETE_MEDIA)
				.extracting(MediaJob::getTargetId, MediaJob::getNextRunAt)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(10L, NOW),
						org.assertj.core.groups.Tuple.tuple(11L, NOW));
		assertThat(created).filteredOn(j -> j.getKind() == MediaJobKind.PURGE_ORIGINAL)
				.extracting(MediaJob::getTargetId, MediaJob::getNextRunAt)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(10L, NOW.plus(Duration.ofDays(30))),
						org.assertj.core.groups.Tuple.tuple(11L, NOW.plus(Duration.ofDays(30))));
	}
}
