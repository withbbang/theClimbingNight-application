package com.theclimbingnight.inquiry.dto;

import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import java.time.Instant;

/** 탈퇴한 문의자는 nickname이 null이다. */
public record InquiryAdminResponse(long id, String title, String body, InquiryStatus status, String answer,
		Instant answeredAt, Instant createdAt, long userId, String nickname) {

	public static InquiryAdminResponse from(Inquiry inquiry) {
		return new InquiryAdminResponse(inquiry.getId(), inquiry.getTitle(), inquiry.getBody(), inquiry.getStatus(),
				inquiry.getAnswer(), inquiry.getAnsweredAt(), inquiry.getCreatedAt(), inquiry.getUser().getId(),
				inquiry.getUser().getNickname());
	}
}
