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
public class KakaoTokenVerifier implements SocialTokenVerifier {

	private final RestClient restClient;
	private final String baseUrl;

	public KakaoTokenVerifier(@Qualifier("socialRestClient") RestClient restClient, AuthProperties properties) {
		this.restClient = restClient;
		this.baseUrl = properties.kakao().baseUrl();
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.KAKAO;
	}

	@Override
	public SocialIdentity verify(String token) {
		Map<?, ?> body;
		try {
			body = restClient.get()
					.uri(baseUrl + "/v2/user/me")
					.header("Authorization", "Bearer " + token)
					.retrieve()
					.body(Map.class);
		} catch (RestClientResponseException e) {
			throw ProviderErrors.from(e);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		}
		Object id = body == null ? null : body.get("id");
		if (id == null) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
		}
		return new SocialIdentity(SocialProvider.KAKAO, String.valueOf(id));
	}

}
