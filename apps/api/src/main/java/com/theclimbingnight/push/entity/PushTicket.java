package com.theclimbingnight.push.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Expo가 발송 접수 뒤 돌려준 티켓. 나중에 영수증을 조회해 무효 토큰을 정리하는 데 쓴다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "push_tickets")
public class PushTicket extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_id", nullable = false, length = 64)
	private String ticketId;

	@Column(name = "expo_push_token", nullable = false, length = 255)
	private String expoPushToken;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	public static PushTicket of(String ticketId, String expoPushToken, Instant sentAt) {
		PushTicket ticket = new PushTicket();
		ticket.ticketId = ticketId;
		ticket.expoPushToken = expoPushToken;
		ticket.sentAt = sentAt;
		return ticket;
	}
}
