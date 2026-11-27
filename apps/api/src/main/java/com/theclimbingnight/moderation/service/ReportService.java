package com.theclimbingnight.moderation.service;

import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.service.CommentCounter;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.moderation.dto.ReportCreateRequest;
import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수와 자동 숨김. 대상 행(게시물·사용자)을 잠근 트랜잭션 안에서 열린 신고 수를 세므로, 동시에 신고가 몰려도
 * 첫 신고 알림은 한 번만 나가고 자동 숨김도 한 번만 일어난다.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

	static final int AUTO_HIDE_REPORTERS = 3;
	private static final String CONSTRAINT_REPORTER_TARGET = "uk_reports_reporter_target";
	/**
	 * 다른 사람이 신고할 수 있는 글 상태. 초안·실패·숨긴 글은 남에게 보이지 않으므로 존재 여부를 흘리지 않게 404다.
	 * 그래서 이미 숨긴 글에는 신고가 쌓이지 않고 격리 작업도 다시 생기지 않는다.
	 */
	private static final Set<PostStatus> REPORTABLE = EnumSet.of(PostStatus.PENDING, PostStatus.VISIBLE);

	private final ReportRepository reports;
	private final PostRepository posts;
	private final CommentRepository comments;
	private final CommentCounter counter;
	private final UserRepository users;
	private final UserBlockRepository blocks;
	private final MediaJobScheduler scheduler;
	private final ApplicationEventPublisher events;

	public ReportService(ReportRepository reports, PostRepository posts, CommentRepository comments,
			CommentCounter counter, UserRepository users, UserBlockRepository blocks, MediaJobScheduler scheduler, ApplicationEventPublisher events) {
		this.reports = reports;
		this.posts = posts;
		this.comments = comments;
		this.counter = counter;
		this.users = users;
		this.blocks = blocks;
		this.scheduler = scheduler;
		this.events = events;
	}

	@Transactional
	public void submit(long reporterId, ReportCreateRequest request) {
		switch (request.targetType()) {
			case POST -> submitPost(reporterId, request);
			case COMMENT -> submitComment(reporterId, request);
			case USER -> submitUser(reporterId, request);
		}
	}

	private void submitPost(long reporterId, ReportCreateRequest request) {
		Post post = posts.findByIdForUpdate(request.targetId())
				.filter(p -> p.getDeletedAt() == null && REPORTABLE.contains(p.getStatus()))
				.orElseThrow(ReportService::notFound);
		if (post.getAuthorId() == reporterId) {
			throw new BusinessException(ErrorCode.REPORT_SELF);
		}
		if (blocks.existsBetween(reporterId, post.getAuthorId())) {
			throw notFound();
		}
		long openReports = save(reporterId, request);
		if (request.reason() == ReportReason.PORTRAIT_PRIVACY || openReports >= AUTO_HIDE_REPORTERS) {
			post.hide(true);
			scheduler.onPostHidden(post);
		}
	}

	/**
	 * 잠금 순서는 게시물 행 다음 댓글 행이다. 게시물을 잠그려면 post_id를 알아야 해서 엔티티를 올리지 않는 조회로 먼저 찾고,
	 * 댓글 엔티티는 게시물을 잠근 뒤 처음 읽는다(그 전에 올리면 잠금 뒤 상태 재확인이 낡은 값을 본다).
	 */
	private void submitComment(long reporterId, ReportCreateRequest request) {
		long postId = comments.findLivePostIdById(request.targetId()).orElseThrow(ReportService::notFound);
		Post post = posts.findByIdForUpdate(postId)
				.filter(p -> p.getDeletedAt() == null && REPORTABLE.contains(p.getStatus()))
				.orElseThrow(ReportService::notFound);
		Comment comment = comments.findByIdForUpdate(request.targetId()).filter(Comment::isShown)
				.orElseThrow(ReportService::notFound);
		if (comment.getAuthorId() == reporterId) {
			throw new BusinessException(ErrorCode.REPORT_SELF);
		}
		if (blocks.existsBetween(reporterId, comment.getAuthorId())) {
			throw notFound();
		}
		long openReports = save(reporterId, request);
		if (openReports >= AUTO_HIDE_REPORTERS) {
			comment.hide(true);
			counter.recount(post);
		}
	}

	private void submitUser(long reporterId, ReportCreateRequest request) {
		if (request.targetId() == reporterId) {
			throw new BusinessException(ErrorCode.REPORT_SELF);
		}
		users.findByIdForUpdate(request.targetId()).filter(User::isActive).orElseThrow(ReportService::notFound);
		save(reporterId, request);
	}

	/**
	 * 신고를 넣고 이 신고를 포함한 열린 신고 수를 돌려준다. 신고는 사람·대상마다 하나라 이 수가 곧 서로 다른 신고자 수다.
	 * 호출한 쪽이 대상 행을 잠근 상태여야 한다.
	 */
	private long save(long reporterId, ReportCreateRequest request) {
		long openBefore = reports.countByTargetTypeAndTargetIdAndStatus(request.targetType(), request.targetId(),
				ReportStatus.OPEN);
		String detail = request.detail() == null || request.detail().isBlank() ? null : request.detail().strip();
		try {
			reports.saveAndFlush(Report.open(reporterId, request.targetType(), request.targetId(), request.reason(),
					detail));
		} catch (DataIntegrityViolationException e) {
			if (ConstraintViolations.violated(e).contains(CONSTRAINT_REPORTER_TARGET)) {
				throw new BusinessException(ErrorCode.REPORT_ALREADY_SUBMITTED);
			}
			throw e;
		}
		if (openBefore == 0) {
			events.publishEvent(new ReportSubmittedEvent(request.targetType(), request.targetId(), reporterId));
		}
		return openBefore + 1;
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.COMMON_NOT_FOUND);
	}
}
