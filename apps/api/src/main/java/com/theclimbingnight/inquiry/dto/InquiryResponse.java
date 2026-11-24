package com.theclimbingnight.inquiry.dto;

import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import java.time.Instant;

/** 답변자는 공개하지 않는다. */
public record InquiryResponse(long id, String title, String body, InquiryStatus status, String answer,
		Instant answeredAt, Instant createdAt) {

	public static InquiryResponse from(Inquiry inquiry) {
		return new InquiryResponse(inquiry.getId(), inquiry.getTitle(), inquiry.getBody(), inquiry.getStatus(),
				inquiry.getAnswer(), inquiry.getAnsweredAt(), inquiry.getCreatedAt());
	}
}
