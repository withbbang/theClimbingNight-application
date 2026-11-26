package com.theclimbingnight.feed.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.dto.PostCreateRequest;
import com.theclimbingnight.feed.dto.PostDraftResponse;
import com.theclimbingnight.feed.dto.PostMediaSpec;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.dto.PostUploadResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.PresignedUpload;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCommandService {

	static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
	private static final String JPEG = "image/jpeg";
	private static final int KEY_BYTES = 16;

	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final UserRepository users;
	private final MediaStorage storage;
	private final MediaJobScheduler scheduler;
	private final PostResponseMapper mapper;
	private final SecureRandom random;
	private final Clock clock;

	public PostCommandService(PostRepository posts, PostMediaRepository mediaRepository, UserRepository users,
			MediaStorage storage, MediaJobScheduler scheduler, PostResponseMapper mapper, SecureRandom random,
			Clock clock) {
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.users = users;
		this.storage = storage;
		this.scheduler = scheduler;
		this.mapper = mapper;
		this.random = random;
		this.clock = clock;
	}

	/** 초안과 사진 행을 만들고 사진마다 업로드 주소를 준다. 저장소가 꺼져 있으면 전부 되돌린다. */
	@Transactional
	public PostDraftResponse createDraft(long userId, PostCreateRequest request) {
		Post post = posts.save(Post.draft(userId));
		List<PostMediaSpec> specs = request.media();
		List<PostMedia> media = new ArrayList<>();
		for (int i = 0; i < specs.size(); i++) {
			PostMediaSpec spec = specs.get(i);
			PostMedia m = PostMedia.image(post.getId(), i, "uploads/" + randomHex() + ".jpg", "p/" + randomHex());
			m.declare(spec.width(), spec.height(), spec.byteSize());
			media.add(m);
		}
		mediaRepository.saveAll(media);
		List<PostUploadResponse> uploads = new ArrayList<>();
		for (PostMedia m : media) {
			PresignedUpload upload = storage.presignUpload(m.getUploadKey(), JPEG, m.getByteSize(), UPLOAD_TTL);
			uploads.add(new PostUploadResponse(m.getId(), upload.url(), upload.headers(),
					upload.expiresAt().atOffset(ZoneOffset.UTC)));
		}
		return new PostDraftResponse(post.getId(), uploads);
	}

	/** 행을 잠근 뒤 상태를 봐서, 같은 글의 동시 게시가 와도 하나만 통과한다. */
	@Transactional
	public PostResponse publish(long userId, long postId, String body) {
		Post post = visibleToAuthor(posts.findByIdForUpdate(postId).orElse(null), userId);
		if (post.getStatus() != PostStatus.DRAFT) {
			throw new BusinessException(ErrorCode.POST_NOT_DRAFT);
		}
		List<PostMedia> media = mediaRepository.findAllByPostIdOrderBySortOrder(postId);
		for (PostMedia m : media) {
			OptionalLong uploaded = storage.uploadedSize(m.getUploadKey());
			if (uploaded.isEmpty() || m.getByteSize() == null || uploaded.getAsLong() != m.getByteSize()) {
				throw new BusinessException(ErrorCode.POST_UPLOAD_INCOMPLETE);
			}
		}
		post.publish(body == null ? "" : body);
		media.forEach(PostMedia::markProcessing);
		scheduler.enqueueProcess(media);
		return toResponse(post, media, userId);
	}

	/** 행을 잠근 뒤 상태를 봐서, 동시에 숨김·복원이 와도 오래된 상태로 고치지 않는다. */
	@Transactional
	public PostResponse update(long userId, long postId, String body) {
		Post post = visibleToAuthor(posts.findByIdForUpdate(postId).orElse(null), userId);
		if (post.getStatus() == PostStatus.HIDDEN_PENDING || post.getStatus() == PostStatus.HIDDEN) {
			throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
		}
		post.editBody(body == null ? "" : body);
		return toResponse(post, mediaRepository.findAllByPostIdOrderBySortOrder(postId), userId);
	}

	/** 작성자나 ADMIN이 지운다. 남의 글은 존재 여부를 흘리지 않으려고 404다. */
	@Transactional
	public void delete(long userId, Role role, long postId) {
		Post post = posts.findByIdForUpdate(postId).orElse(null);
		if (post == null || post.getDeletedAt() != null || (post.getAuthorId() != userId && role != Role.ADMIN)) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		post.softDelete(clock.instant());
		scheduler.onPostRemoved(post);
	}

	private Post visibleToAuthor(Post post, long userId) {
		if (post == null || post.getDeletedAt() != null || post.getAuthorId() != userId) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		return post;
	}

	private PostResponse toResponse(Post post, List<PostMedia> media, long viewerId) {
		String nickname = users.findById(post.getAuthorId()).map(User::getNickname).orElse("");
		return mapper.toResponse(post, nickname, media, viewerId);
	}

	private String randomHex() {
		byte[] bytes = new byte[KEY_BYTES];
		random.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
