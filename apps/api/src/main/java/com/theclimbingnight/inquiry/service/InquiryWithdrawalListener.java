package com.theclimbingnight.inquiry.service;

import com.theclimbingnight.inquiry.repository.InquiryRepository;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 트랜잭션 안에서 동기 실행된다. 문의와 답변을 지운다. */
@Component
public class InquiryWithdrawalListener {

	private final InquiryRepository inquiryRepository;

	public InquiryWithdrawalListener(InquiryRepository inquiryRepository) {
		this.inquiryRepository = inquiryRepository;
	}

	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE + 2)
	@Transactional
	public void on(UserWithdrawnEvent event) {
		inquiryRepository.deleteAllByUserId(event.userId());
	}
}
