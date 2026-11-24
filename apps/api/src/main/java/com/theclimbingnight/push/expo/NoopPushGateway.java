package com.theclimbingnight.push.expo;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** app.push.enabled가 true가 아닐 때 쓴다. 성공도 실패도 아닌 빈 결과를 돌려준다. */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopPushGateway implements PushGateway {

	@Override
	public List<PushTicketResult> send(List<PushMessage> messages) {
		return List.of();
	}

	@Override
	public Map<String, PushReceipt> receipts(List<String> ticketIds) {
		return Map.of();
	}
}
