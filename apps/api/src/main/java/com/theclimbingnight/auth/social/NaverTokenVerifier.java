package com.theclimbingnight.auth.social;

import com.theclimbingnight.auth.AuthProperties;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverTokenVerifier implements SocialTokenVerifier {

	private final RestClient restClient;
	private final String baseUrl;

	public NaverTokenVerifier(@Qualifier("socialRestClient") RestClient restClient, AuthProperties properties) {
		this.restClient = restClient;
		this.baseUrl = properties.naver().baseUrl();
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.NAVER;
	}

	@Override
	public SocialIdentity verify(String token) {
		Map<?, ?> body;
		try {
			body = restClient.get()
					.uri(baseUrl + "/v1/nid/me")
					.header("Authorization", "Bearer " + token)
					.retrieve()
					.body(Map.class);
		} catch (RestClientResponseException e) {
			throw ProviderErrors.from(e);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		}
		if (body == null || !"00".equals(body.get("resultcode")) || !(body.get("response") instanceof Map<?, ?> response)
				|| response.get("id") == null) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
		}
		return new SocialIdentity(SocialProvider.NAVER, String.valueOf(response.get("id")));
	}

}
