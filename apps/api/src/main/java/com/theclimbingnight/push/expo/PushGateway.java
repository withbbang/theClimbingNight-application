package com.theclimbingnight.push.expo;

import java.util.List;
import java.util.Map;

public interface PushGateway {

	/** 메시지 순서대로 티켓 결과를 돌려준다. 보내지 않는 구현은 빈 목록을 돌려준다. */
	List<PushTicketResult> send(List<PushMessage> messages);

	/** 결과가 아직 없는 티켓 ID는 맵에 없다. */
	Map<String, PushReceipt> receipts(List<String> ticketIds);
}
