package com.theclimbingnight.notice.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.notice.dto.NoticeRequest;
import com.theclimbingnight.notice.dto.NoticeResponse;
import com.theclimbingnight.notice.dto.NoticeSummaryResponse;
import com.theclimbingnight.notice.entity.Notice;
import com.theclimbingnight.notice.repository.NoticeRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NoticeService {

	private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"),
			Sort.Order.desc("id"));

	private final NoticeRepository noticeRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public NoticeService(NoticeRepository noticeRepository, UserRepository userRepository,
			ApplicationEventPublisher eventPublisher, Clock clock) {
		this.noticeRepository = noticeRepository;
		this.userRepository = userRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	public PageResponse<NoticeSummaryResponse> list(int page, int size) {
		return PageResponse.from(noticeRepository.findAll(PageRequest.of(page, size, LIST_ORDER)),
				NoticeSummaryResponse::from);
	}

	public NoticeResponse get(long id) {
		return NoticeResponse.from(find(id));
	}

	/** 푸시 발송은 이 이벤트를 받는 쪽이 커밋 뒤에 한다. */
	@Transactional
	public NoticeResponse create(long adminId, NoticeRequest request) {
		User admin = userRepository.findById(adminId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		Notice notice = noticeRepository.saveAndFlush(
				Notice.create(request.title(), request.body(), request.pinned(), admin, clock.instant()));
		eventPublisher.publishEvent(new NoticePublishedEvent(notice.getId(), adminId, notice.getTitle()));
		return NoticeResponse.from(notice);
	}

	@Transactional
	public NoticeResponse update(long id, NoticeRequest request) {
		Notice notice = find(id);
		notice.update(request.title(), request.body(), request.pinned());
		return NoticeResponse.from(noticeRepository.saveAndFlush(notice));
	}

	@Transactional
	public void delete(long id) {
		noticeRepository.delete(find(id));
	}

	private Notice find(long id) {
		return noticeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}
}
