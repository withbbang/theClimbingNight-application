package com.theclimbingnight.moderation.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.feed.dto.PostResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.feed.service.PostResponseMapper;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.moderation.dto.ReportTargetActionRequest.Action;
import com.theclimbingnight.moderation.dto.ReportTargetResponse;
import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.moderation.repository.ReportTargetSummary;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영진의 신고 목록과 조치(숨김·복원·기각). 게시물 상태를 바꾸는 조치는 모두 게시물 행을 잠근 뒤 상태를 본다. */
@Service
@Transactional(readOnly = true)
public class ModerationService {

	static final Duration QUARANTINE_URL_TTL = Duration.ofMinutes(5);
	private static final String DELETED = "DELETED";

	private final ReportRepository reports;
	private final PostRepository posts;
	private final PostMediaRepository mediaRepository;
	private final UserRepository users;
	private final MediaJobScheduler scheduler;
	private final MediaStorage storage;
	private final PostResponseMapper mapper;
	private final Clock clock;

	public ModerationService(ReportRepository reports, PostRepository posts, PostMediaRepository mediaRepository,
			UserRepository users, MediaJobScheduler scheduler, MediaStorage storage, PostResponseMapper mapper,
			Clock clock) {
		this.reports = reports;
		this.posts = posts;
		this.mediaRepository = mediaRepository;
		this.users = users;
		this.scheduler = scheduler;
		this.storage = storage;
		this.mapper = mapper;
		this.clock = clock;
	}

	/** 열린 신고가 있는 대상만, 최근 신고순. 차단 관계와 무관하다. */
	public PageResponse<ReportTargetResponse> list(int page, int size) {
		Page<ReportTargetSummary> summaries = reports.findOpenTargets(PageRequest.of(page, size));
		List<Long> postIds = idsOf(summaries, ReportTargetType.POST);
		List<Long> userIds = idsOf(summaries, ReportTargetType.USER);
		Map<ReportTargetType, Map<Long, List<Report>>> open = new EnumMap<>(ReportTargetType.class);
		open.put(ReportTargetType.POST, openReports(ReportTargetType.POST, postIds));
		open.put(ReportTargetType.USER, openReports(ReportTargetType.USER, userIds));
		Map<Long, Post> postById = postIds.isEmpty() ? Map.of()
				: posts.findAllById(postIds).stream().collect(Collectors.toMap(Post::getId, Function.identity()));
		Map<Long, List<PostMedia>> mediaByPost = postIds.isEmpty() ? Map.of()
				: mediaRepository.findAllByPostIdInOrderBySortOrder(postIds).stream()
						.collect(Collectors.groupingBy(PostMedia::getPostId));
		Set<Long> wantedUsers = new HashSet<>(userIds);
		postById.values().forEach(p -> wantedUsers.add(p.getAuthorId()));
		Map<Long, User> userById = wantedUsers.isEmpty() ? Map.of()
				: users.findAllById(wantedUsers).stream().collect(Collectors.toMap(User::getId, Function.identity()));
		return PageResponse.from(summaries, s -> {
			List<Report> targetReports = open.get(s.targetType()).getOrDefault(s.targetId(), List.of());
			return s.targetType() == ReportTargetType.POST
					? postTarget(s, targetReports, postById.get(s.targetId()), mediaByPost, userById)
					: userTarget(s, targetReports, userById.get(s.targetId()));
		});
	}

	@Transactional
	public void act(long adminId, ReportTargetType targetType, long targetId, Action action) {
		if (targetType == ReportTargetType.COMMENT
				|| (targetType == ReportTargetType.USER && action != Action.DISMISS)) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		switch (action) {
			case HIDE -> {
				hide(lockedPost(targetId));
				close(targetType, targetId, ReportStatus.ACTIONED, adminId);
			}
			case RESTORE -> {
				restore(lockedPost(targetId));
				close(targetType, targetId, ReportStatus.DISMISSED, adminId);
			}
			case DISMISS -> {
				if (targetType == ReportTargetType.POST) {
					releaseAutoHidden(targetId);
				}
				close(targetType, targetId, ReportStatus.DISMISSED, adminId);
			}
		}
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
			Map<Long, List<PostMedia>> mediaByPost, Map<Long, User> userById) {
		if (post == null || post.getDeletedAt() != null) {
			return response(s, targetReports, DELETED, null, List.of(), null);
		}
		List<PostMedia> media = mediaByPost.getOrDefault(post.getId(), List.of());
		User author = userById.get(post.getAuthorId());
		PostResponse body = mapper.toResponse(post, author == null ? null : author.getNickname(), media, null);
		List<String> quarantineUrls = isHidden(post) ? media.stream()
				.filter(m -> m.getStatus() == PostMediaStatus.READY)
				.map(m -> storage.presignQuarantineGet(m.displayKey(), QUARANTINE_URL_TTL).toString())
				.toList() : List.of();
		return response(s, targetReports, post.getStatus().name(), body, quarantineUrls, null);
	}

	private ReportTargetResponse userTarget(ReportTargetSummary s, List<Report> targetReports, User user) {
		return response(s, targetReports, user == null ? DELETED : user.getStatus().name(), null, List.of(),
				user == null ? null : user.getNickname());
	}

	private static ReportTargetResponse response(ReportTargetSummary s, List<Report> targetReports,
			String targetStatus, PostResponse post, List<String> quarantineUrls, String userNickname) {
		Map<ReportReason, Integer> counts = new EnumMap<>(ReportReason.class);
		targetReports.forEach(r -> counts.merge(r.getReason(), 1, Integer::sum));
		Map<String, Integer> reasons = new LinkedHashMap<>();
		counts.forEach((reason, count) -> reasons.put(reason.name(), count));
		List<String> details = targetReports.stream().sorted(Comparator.comparing(Report::getId).reversed())
				.map(Report::getDetail).filter(Objects::nonNull).toList();
		return new ReportTargetResponse(s.targetType().name(), s.targetId(), targetStatus,
				s.reportCount().intValue(), reasons, details, s.lastReportedAt().atOffset(ZoneOffset.UTC), post,
				quarantineUrls, userNickname);
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
