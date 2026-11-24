package com.theclimbingnight.push.expo;

/** 성공이면 error가 null, 실패면 Expo가 준 details.error(없으면 UNKNOWN). */
public record PushTicketResult(String ticketId, String error) {
}
