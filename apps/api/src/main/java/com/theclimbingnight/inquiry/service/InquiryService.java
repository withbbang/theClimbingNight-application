package com.theclimbingnight.inquiry.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.inquiry.dto.InquiryAdminResponse;
import com.theclimbingnight.inquiry.dto.InquiryAnswerRequest;
import com.theclimbingnight.inquiry.dto.InquiryCreateRequest;
import com.theclimbingnight.inquiry.dto.InquiryResponse;
import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import com.theclimbingnight.inquiry.repository.InquiryRepository;
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
public class InquiryService {

	private static final Sort ID_DESC = Sort.by(Sort.Order.desc("id"));

	private final InquiryRepository inquiryRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public InquiryService(InquiryRepository inquiryRepository, UserRepository userRepository,
			ApplicationEventPublisher eventPublisher, Clock clock) {
		this.inquiryRepository = inquiryRepository;
		this.userRepository = userRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public InquiryResponse create(long userId, InquiryCreateRequest request) {
		// 탈퇴와 겹치면 지운 뒤에 문의가 남으므로 사용자 행을 잠그고 다시 확인한다.
		User user = userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		Inquiry inquiry = inquiryRepository.saveAndFlush(Inquiry.create(user, request.title(), request.body()));
		return InquiryResponse.from(inquiry);
	}

	public PageResponse<InquiryResponse> mine(long userId, int page, int size) {
		return PageResponse.from(inquiryRepository.findAllByUserId(userId, PageRequest.of(page, size, ID_DESC)),
				InquiryResponse::from);
	}

	/** 남의 문의는 존재 여부를 숨기려고 404로 답한다. */
	public InquiryResponse getMine(long userId, long id) {
		Inquiry inquiry = inquiryRepository.findById(id).filter(i -> i.getUser().getId() == userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		return InquiryResponse.from(inquiry);
	}

	public PageResponse<InquiryAdminResponse> list(InquiryStatus status, int page, int size) {
		return PageResponse.from(inquiryRepository.findAllForAdmin(status, PageRequest.of(page, size, ID_DESC)),
				InquiryAdminResponse::from);
	}

	public InquiryAdminResponse get(long id) {
		return InquiryAdminResponse.from(inquiryRepository.findWithUserById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND)));
	}

	/**
	 * answered_by가 사용자 행에 FK 잠금을 잡으므로 운영진 행을 먼저 잠근다(사용자 → 문의).
	 * 푸시 발송은 이 이벤트를 받는 쪽이 커밋 뒤에 한다.
	 */
	@Transactional
	public InquiryAdminResponse answer(long id, long adminId, InquiryAnswerRequest request) {
		User admin = userRepository.findByIdForUpdate(adminId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
		Inquiry inquiry = inquiryRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		boolean first = !inquiry.isAnswered();
		inquiry.answer(request.answer(), admin, clock.instant());
		inquiryRepository.saveAndFlush(inquiry);
		if (first) {
			eventPublisher.publishEvent(new InquiryAnsweredEvent(inquiry.getId(), inquiry.getUser().getId()));
		}
		return InquiryAdminResponse.from(inquiry);
	}
}
