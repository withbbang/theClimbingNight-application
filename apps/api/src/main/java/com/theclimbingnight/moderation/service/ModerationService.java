package com.theclimbingnight.moderation.service;

import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.entity.CommentStatus;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.service.CommentCounter;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.feed.dto.PostAuthorResponse;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.feed.service.PostResponseMapper;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.moderation.dto.ReportCommentResponse;
import com.theclimbingnight.moderation.dto.ReportTargetActionRequest.Action;
import com.theclimbingnight.moderation.dto.ReportTargetResponse;
import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.moderation.repository.ReportTargetSummary;
import com.theclimbingnight.profile.dto.ProfileImageUrls;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserAvatars;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진의 신고 목록과 조치(숨김·복원·기각). 게시물 상태를 바꾸는 조치는 모두 게시물 행을 잠근 뒤 상태를 보고,
 * 댓글 조치는 게시물 행 다음 댓글 행을 잠근다. 댓글 조치는 사진 작업을 만들지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ModerationService {

	static final Duration QUARANTINE_URL_TTL = Duration.ofMinutes(5);
	private static final String DELETED = "DELETED";

	private final ReportRepository reports;
	private final PostRepository posts;
	private final CommentRepository comments;
	private final CommentCounter counter;
	private final PostMediaRepository mediaRepository;
	private final UserRepository users;
	private final MediaJobRepository jobs;
	private final MediaJobScheduler scheduler;
	private final MediaStorage storage;
	private final PostResponseMapper mapper;
	private final MediaUrls urls;
	private final UserAvatars avatars;
	private final Clock clock;

	public ModerationService(ReportRepository reports, PostRepository posts, CommentRepository comments,
			CommentCounter counter, PostMediaRepository mediaRepository, UserRepository users, MediaJobRepository jobs, MediaJobScheduler scheduler, MediaStorage storage,
			PostResponseMapper mapper, MediaUrls urls, UserAvatars avatars, Clock clock) {
		this.reports = reports;
		this.posts = posts;
		this.comments = comments;
		this.counter = counter;
		this.mediaRepository = mediaRepository;
		this.users = users;
		this.jobs = jobs;
		this.scheduler = scheduler;
		this.storage = storage;
		this.mapper = mapper;
		this.urls = urls;
		this.avatars = avatars;
		this.clock = clock;
	}

	/** 열린 신고가 있는 대상만, 최근 신고순. 차단 관계와 무관하다. */
	public PageResponse<ReportTargetResponse> list(int page, int size) {
		Page<ReportTargetSummary> summaries = reports.findOpenTargets(PageRequest.of(page, size));
		List<Long> postIds = idsOf(summaries, ReportTargetType.POST);
		List<Long> commentIds = idsOf(summaries, ReportTargetType.COMMENT);
		List<Long> userIds = idsOf(summaries, ReportTargetType.USER);
		Map<ReportTargetType, Map<Long, List<Report>>> open = new EnumMap<>(ReportTargetType.class);
		open.put(ReportTargetType.POST, openReports(ReportTargetType.POST, postIds));
		open.put(ReportTargetType.COMMENT, openReports(ReportTargetType.COMMENT, commentIds));
		open.put(ReportTargetType.USER, openReports(ReportTargetType.USER, userIds));
		Map<Long, Comment> commentById = commentIds.isEmpty() ? Map.of()
				: comments.findAllById(commentIds).stream().collect(Collectors.toMap(Comment::getId, Function.identity()));
		// 댓글 대상은 원 게시물도 함께 보여 준다.
		List<Long> shownPostIds = new ArrayList<>(postIds);
		commentById.values().stream().map(Comment::getPostId).distinct().filter(id -> !postIds.contains(id))
				.forEach(shownPostIds::add);
		Map<Long, Post> postById = shownPostIds.isEmpty() ? Map.of()
				: posts.findAllById(shownPostIds).stream().collect(Collectors.toMap(Post::getId, Function.identity()));
		Map<Long, List<PostMedia>> mediaByPost = shownPostIds.isEmpty() ? Map.of()
				: mediaRepository.findAllByPostIdInOrderBySortOrder(shownPostIds).stream()
						.collect(Collectors.groupingBy(PostMedia::getPostId));
		Set<Long> cleanupFailed = postIds.isEmpty() ? Set.of() : Set.copyOf(jobs.findPostIdsWithFailedCleanup(postIds));
		Set<Long> wantedUsers = new HashSet<>(userIds);
		postById.values().forEach(p -> wantedUsers.add(p.getAuthorId()));
		commentById.values().forEach(c -> wantedUsers.add(c.getAuthorId()));
		Map<Long, User> userById = wantedUsers.isEmpty() ? Map.of()
				: users.findAllById(wantedUsers).stream().collect(Collectors.toMap(User::getId, Function.identity()));
		Map<Long, ProfileImageUrls> images = avatars.of(wantedUsers);
		return PageResponse.from(summaries, s -> {
			List<Report> targetReports = open.get(s.targetType()).getOrDefault(s.targetId(), List.of());
			return switch (s.targetType()) {
				case POST -> postTarget(s, targetReports, postById.get(s.targetId()), mediaByPost, userById,
						images, cleanupFailed.contains(s.targetId()));
				case COMMENT -> commentTarget(s, targetReports, commentById.get(s.targetId()), postById,
						mediaByPost, userById, images);
				case USER -> userTarget(s, targetReports, userById.get(s.targetId()));
			};
		});
	}

	@Transactional
	public void act(long adminId, ReportTargetType targetType, long targetId, Action action) {
		if (targetType == ReportTargetType.USER && action != Action.DISMISS) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		boolean comment = targetType == ReportTargetType.COMMENT;
		switch (action) {
			case HIDE -> {
				if (comment) {
					hideComment(targetId);
				}
				else {
					hide(lockedPost(targetId));
				}
				close(targetType, targetId, ReportStatus.ACTIONED, adminId);
			}
			case RESTORE -> {
				if (comment) {
					restoreComment(targetId);
				}
				else {
					restore(lockedPost(targetId));
				}
				close(targetType, targetId, ReportStatus.DISMISSED, adminId);
			}
			case DISMISS -> {
				if (targetType == ReportTargetType.POST) {
					releaseAutoHidden(targetId);
				}
				else if (comment) {
					releaseAutoHiddenComment(targetId);
				}
				close(targetType, targetId, ReportStatus.DISMISSED, adminId);
			}
		}
	}

	/** 잠근 게시물과 그 아래 잠근 댓글. */
	private record LockedComment(Post post, Comment comment) {
	}

	/**
	 * 잠금 순서는 게시물 행 다음 댓글 행이다. 게시물을 잠그려면 post_id가 필요해 엔티티를 올리지 않는 조회로 먼저 찾고,
	 * 댓글 엔티티는 게시물을 잠근 뒤 처음 읽는다(그 전에 올리면 잠금 뒤 재확인이 낡은 값을 본다).
	 * 지운 댓글이나 지운 글의 댓글이면 비어 있다.
	 */
	private Optional<LockedComment> lockComment(long commentId) {
		return comments.findLivePostIdById(commentId)
				.flatMap(postId -> posts.findByIdForUpdate(postId).filter(p -> p.getDeletedAt() == null))
				.flatMap(post -> comments.findByIdForUpdate(commentId).filter(c -> c.getDeletedAt() == null)
						.map(c -> new LockedComment(post, c)));
	}

	private LockedComment lockedComment(long commentId) {
		return lockComment(commentId).orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	private void hideComment(long commentId) {
		LockedComment locked = lockedComment(commentId);
		if (locked.comment().getStatus() == CommentStatus.HIDDEN) {
			return;
		}
		locked.comment().hide(false);
		counter.recount(locked.post());
	}

	/** 숨긴 댓글만 되살린다. 숨기지 않은 댓글이면 신고만 기각된다. */
	private void restoreComment(long commentId) {
		LockedComment locked = lockedComment(commentId);
		if (locked.comment().isHidden()) {
			locked.comment().restore();
			counter.recount(locked.post());
		}
	}

	/** 기각하면 자동 숨김(HIDDEN_PENDING) 댓글은 되살린다. 확정 숨김은 그대로고, 지운 댓글·글이어도 404로 막지 않는다. */
	private void releaseAutoHiddenComment(long commentId) {
		lockComment(commentId).filter(l -> l.comment().getStatus() == CommentStatus.HIDDEN_PENDING)
				.ifPresent(l -> {
					l.comment().restore();
					counter.recount(l.post());
				});
	}

	/**
	 * 신고를 기각하면 숨길 이유가 없으므로 자동 숨김(HIDDEN_PENDING)된 글은 되살린다. 운영진이 확정한 숨김(HIDDEN)은 그대로 둔다.
	 * 지웠거나 없는 글이어도 신고는 닫아야 하므로 404로 막지 않는다.
	 */
	private void releaseAutoHidden(long postId) {
		posts.findByIdForUpdate(postId)
				.filter(p -> p.getDeletedAt() == null && p.getStatus() == PostStatus.HIDDEN_PENDING)
				.ifPresent(this::restore);
	}

	/** 자동 숨김(HIDDEN_PENDING)이면 격리 작업이 이미 있으니 상태만 확정한다. */
	private void hide(Post post) {
		PostStatus before = post.getStatus();
		if (before == PostStatus.HIDDEN) {
			return;
		}
		post.hide(false);
		if (before != PostStatus.HIDDEN_PENDING) {
			scheduler.onPostHidden(post);
		}
	}

	/** 숨긴 글만 되살린다. 숨기지 않은 글이면 신고만 기각된다. */
	private void restore(Post post) {
		if (isHidden(post)) {
			post.restore();
			scheduler.onPostRestored(post);
		}
	}

	private Post lockedPost(long postId) {
		return posts.findByIdForUpdate(postId)
				.filter(p -> p.getDeletedAt() == null && p.getStatus() != PostStatus.DRAFT
						&& p.getStatus() != PostStatus.FAILED)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	private void close(ReportTargetType targetType, long targetId, ReportStatus result, long adminId) {
		Instant now = clock.instant();
		reports.findAllByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.OPEN)
				.forEach(r -> r.handle(result, adminId, now));
	}

	private ReportTargetResponse postTarget(ReportTargetSummary s, List<Report> targetReports, Post post,
			Map<Long, List<PostMedia>> mediaByPost, Map<Long, User> userById,
			Map<Long, ProfileImageUrls> images, boolean cleanupFailed) {
		if (post == null || post.getDeletedAt() != null) {
			return response(s, targetReports, DELETED, null, List.of(), null, null, cleanupFailed);
		}
		List<PostMedia> media = mediaByPost.getOrDefault(post.getId(), List.of());
		User author = userById.get(post.getAuthorId());
		PostResponse body = mapper.toResponse(post, author == null ? null : author.getNickname(),
				UserAvatars.smallUrl(images, post.getAuthorId()), media, null, false);
		List<String> quarantineUrls = isHidden(post) ? media.stream()
				.filter(m -> m.getStatus() != PostMediaStatus.UPLOADING)
				.map(this::reviewUrl)
				.flatMap(Optional::stream)
				.toList() : List.of();
		return response(s, targetReports, post.getStatus().name(), body, quarantineUrls, null, null, cleanupFailed);
	}

	/**
	 * 숨긴 글의 사진을 운영진이 볼 주소. 사진 상태로는 격리 여부를 알 수 없어(복원 직후 다시 숨긴 PROCESSING 사진도 격리돼 있고,
	 * 숨긴 직후의 READY 사진은 아직 격리 전이다) 저장소를 HEAD로 본다. 격리본이 있으면 격리본 presigned GET, 격리 전이라
	 * 공개본이 있으면 공개 주소, 둘 다 없으면 뺀다. 사진마다 HEAD 1~2번.
	 */
	private Optional<String> reviewUrl(PostMedia media) {
		String key = media.displayKey();
		if (storage.quarantineExists(key)) {
			return Optional.of(storage.presignQuarantineGet(key, QUARANTINE_URL_TTL).toString());
		}
		if (storage.publicExists(key)) {
			return Optional.of(urls.publicUrl(key));
		}
		return Optional.empty();
	}

	private ReportTargetResponse commentTarget(ReportTargetSummary s, List<Report> targetReports, Comment comment,
			Map<Long, Post> postById, Map<Long, List<PostMedia>> mediaByPost, Map<Long, User> userById,
			Map<Long, ProfileImageUrls> images) {
		Post post = comment == null ? null : postById.get(comment.getPostId());
		if (comment == null || comment.getDeletedAt() != null || post == null || post.getDeletedAt() != null) {
			return response(s, targetReports, DELETED, null, List.of(), null, null, false);
		}
		User postAuthor = userById.get(post.getAuthorId());
		PostResponse body = mapper.toResponse(post, postAuthor == null ? null : postAuthor.getNickname(),
				UserAvatars.smallUrl(images, post.getAuthorId()), mediaByPost.getOrDefault(post.getId(), List.of()), null, false);
		User commentAuthor = userById.get(comment.getAuthorId());
		ReportCommentResponse shown = new ReportCommentResponse(comment.getId(), comment.getPostId(),
				new PostAuthorResponse(comment.getAuthorId(), commentAuthor == null ? null : commentAuthor.getNickname(),
						UserAvatars.smallUrl(images, comment.getAuthorId())),
				comment.getBody());
		return response(s, targetReports, comment.getStatus().name(), body, List.of(), shown, null, false);
	}

	private ReportTargetResponse userTarget(ReportTargetSummary s, List<Report> targetReports, User user) {
		return response(s, targetReports, user == null ? DELETED : user.getStatus().name(), null, List.of(), null,
				user == null ? null : user.getNickname(), false);
	}

	private static ReportTargetResponse response(ReportTargetSummary s, List<Report> targetReports,
			String targetStatus, PostResponse post, List<String> quarantineUrls, ReportCommentResponse comment,
			String userNickname, boolean cleanupFailed) {
		Map<ReportReason, Integer> counts = new EnumMap<>(ReportReason.class);
		targetReports.forEach(r -> counts.merge(r.getReason(), 1, Integer::sum));
		Map<String, Integer> reasons = new LinkedHashMap<>();
		counts.forEach((reason, count) -> reasons.put(reason.name(), count));
		List<String> details = targetReports.stream().sorted(Comparator.comparing(Report::getId).reversed())
				.map(Report::getDetail).filter(Objects::nonNull).toList();
		return new ReportTargetResponse(s.targetType().name(), s.targetId(), targetStatus,
				s.reportCount().intValue(), reasons, details, s.lastReportedAt().atOffset(ZoneOffset.UTC), post,
				quarantineUrls, comment, userNickname, cleanupFailed);
	}

	private Map<Long, List<Report>> openReports(ReportTargetType type, List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return reports.findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus.OPEN, type, ids).stream()
				.collect(Collectors.groupingBy(Report::getTargetId));
	}

	private static List<Long> idsOf(Page<ReportTargetSummary> summaries, ReportTargetType type) {
		return summaries.getContent().stream().filter(s -> s.targetType() == type).map(ReportTargetSummary::targetId)
				.toList();
	}

	private static boolean isHidden(Post post) {
		return post.getStatus() == PostStatus.HIDDEN || post.getStatus() == PostStatus.HIDDEN_PENDING;
	}
}
