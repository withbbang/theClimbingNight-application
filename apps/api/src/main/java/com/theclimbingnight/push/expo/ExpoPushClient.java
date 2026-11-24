package com.theclimbingnight.push.expo;

import com.theclimbingnight.push.PushProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Expo 푸시 API 클라이언트. 예외 메시지·원인에 응답 본문, 요청 본문, 토큰을 담지 않는다
 * (푸시 토큰과 access token이 로그에 남지 않게, 원본 예외는 버리고 코드만 가진 예외로 바꾼다).
 */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "enabled", havingValue = "true")
public class ExpoPushClient implements PushGateway {

	private static final String UNKNOWN = "UNKNOWN";
	/** 외부 값이 로그에 그대로 섞이지 않게 코드 모양만 받는다. */
	private static final java.util.regex.Pattern ERROR_CODE = java.util.regex.Pattern.compile("[A-Za-z_]{1,64}");
	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;
	private final PushProperties properties;

	public ExpoPushClient(@Qualifier("pushRestClient") RestClient restClient, PushProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public List<PushTicketResult> send(List<PushMessage> messages) {
		if (messages.isEmpty()) {
			return List.of();
		}
		List<Map<String, Object>> body = new ArrayList<>(messages.size());
		for (PushMessage message : messages) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("to", message.to());
			item.put("title", message.title());
			item.put("body", message.body());
			if (message.data() != null) {
				item.put("data", message.data());
			}
			item.put("sound", "default");
			item.put("channelId", "default");
			item.put("priority", "high");
			body.add(item);
		}
		Map<String, Object> response = post("/--/api/v2/push/send", body);
		if (!(response.get("data") instanceof List<?> data) || data.size() != messages.size()) {
			throw new PushRequestException("TICKET_COUNT_MISMATCH");
		}
		List<PushTicketResult> results = new ArrayList<>(data.size());
		for (Object entry : data) {
			if (!(entry instanceof Map<?, ?> ticket)) {
				throw new PushRequestException("INVALID_RESPONSE");
			}
			if ("ok".equals(ticket.get("status")) && ticket.get("id") instanceof String id && !id.isBlank()) {
				results.add(new PushTicketResult(id, null));
			} else {
				results.add(new PushTicketResult(null, errorOf(ticket)));
			}
		}
		return results;
	}

	@Override
	public Map<String, PushReceipt> receipts(List<String> ticketIds) {
		if (ticketIds.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> response = post("/--/api/v2/push/getReceipts", Map.of("ids", ticketIds));
		if (!(response.get("data") instanceof Map<?, ?> data)) {
			throw new PushRequestException("INVALID_RESPONSE");
		}
		Map<String, PushReceipt> receipts = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : data.entrySet()) {
			if (!(entry.getKey() instanceof String id) || !(entry.getValue() instanceof Map<?, ?> receipt)) {
				throw new PushRequestException("INVALID_RESPONSE");
			}
			receipts.put(id, new PushReceipt("ok".equals(receipt.get("status")) ? null : errorOf(receipt)));
		}
		return receipts;
	}

	private static String errorOf(Map<?, ?> item) {
		if (item.get("details") instanceof Map<?, ?> details && details.get("error") instanceof String error
				&& ERROR_CODE.matcher(error).matches()) {
			return error;
		}
		return UNKNOWN;
	}

	private Map<String, Object> post(String path, Object body) {
		Map<String, Object> response;
		try {
			response = restClient.post()
					.uri(properties.baseUrl() + path)
					.headers(headers -> {
						String token = properties.accessToken();
						if (token != null && !token.isBlank()) {
							headers.setBearerAuth(token);
						}
					})
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(MAP_TYPE);
		} catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			String code = "HTTP_" + status;
			throw (status == 429 || status >= 500) ? new PushTransientException(code) : new PushRequestException(code);
		} catch (ResourceAccessException e) {
			throw new PushTransientException("CONNECTION");
		} catch (PushTransientException | PushRequestException e) {
			throw e;
		} catch (RestClientException e) {
			throw new PushRequestException("INVALID_RESPONSE");
		} catch (RuntimeException e) {
			// 예: 액세스 토큰에 줄바꿈이 있으면 JDK HttpRequest가 헤더 값을 담은 IllegalArgumentException을 던진다. 원본은 버린다.
			throw new PushRequestException("CLIENT_ERROR");
		}
		if (response == null) {
			throw new PushRequestException("INVALID_RESPONSE");
		}
		if (response.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
			throw new PushRequestException(errorsCode(errors.get(0)));
		}
		return response;
	}

	/** Expo가 준 코드가 대문자·밑줄로만 이뤄졌을 때만 쓴다. 그 밖의 값은 로그에 섞여 들어오지 않게 버린다. */
	private static String errorsCode(Object first) {
		if (first instanceof Map<?, ?> error && error.get("code") instanceof String code && code.matches("[A-Z_]{1,64}")) {
			return code;
		}
		return "ERRORS";
	}
}
