package com.theclimbingnight.inquiry.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import com.theclimbingnight.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inquiries")
public class Inquiry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private InquiryStatus status;

	@Column(columnDefinition = "TEXT")
	private String answer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "answered_by")
	private User answerer;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "answered_at")
	private Instant answeredAt;

	/** 제목은 앞뒤 공백을 지우고, 본문은 그대로 둔다. */
	public static Inquiry create(User user, String title, String body) {
		Inquiry inquiry = new Inquiry();
		inquiry.user = user;
		inquiry.title = title.strip();
		inquiry.body = body;
		inquiry.status = InquiryStatus.OPEN;
		return inquiry;
	}

	/** 이미 답변된 문의에 다시 호출하면 답변을 덮어쓴다(오타 수정). */
	public void answer(String answer, User admin, Instant now) {
		this.answer = answer;
		this.answerer = admin;
		this.answeredAt = now;
		this.status = InquiryStatus.ANSWERED;
	}

	public boolean isAnswered() {
		return status == InquiryStatus.ANSWERED;
	}
}
