package com.theclimbingnight.moderation.service;

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
	private final UserRepository users;
	private final UserBlockRepository blocks;
	private final MediaJobScheduler scheduler;
	private final ApplicationEventPublisher events;

	public ReportService(ReportRepository reports, PostRepository posts, UserRepository users,
			UserBlockRepository blocks, MediaJobScheduler scheduler, ApplicationEventPublisher events) {
		this.reports = reports;
		this.posts = posts;
		this.users = users;
		this.blocks = blocks;
		this.scheduler = scheduler;
		this.events = events;
	}

	@Transactional
	public void submit(long reporterId, ReportCreateRequest request) {
		switch (request.targetType()) {
			case POST -> submitPost(reporterId, request);
			case USER -> submitUser(reporterId, request);
			default -> throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
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
