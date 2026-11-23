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
	private final String appId;

	public KakaoTokenVerifier(@Qualifier("socialRestClient") RestClient restClient, AuthProperties properties) {
		this.restClient = restClient;
		this.baseUrl = properties.kakao().baseUrl();
		this.appId = properties.kakao().appId();
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.KAKAO;
	}

	/** 카카오 토큰 정보 조회(GET /v1/user/access_token_info). 응답: id(Long), expires_in(Integer), app_id(Integer). */
	@Override
	public SocialIdentity verify(String token) {
		Map<?, ?> body;
		try {
			body = restClient.get()
					.uri(baseUrl + "/v1/user/access_token_info")
					.header("Authorization", "Bearer " + token)
					.retrieve()
					.body(Map.class);
		} catch (RestClientResponseException e) {
			throw ProviderErrors.from(e);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
		}
		Object id = body == null ? null : body.get("id");
		Object tokenAppId = body == null ? null : body.get("app_id");
		// 다른 앱에서 발급된 토큰을 받아주면 안 된다. 설정이 비어 있어도 통과시키지 않는다(fail-closed).
		if (id == null || tokenAppId == null || appId == null || appId.isBlank()
				|| !appId.equals(String.valueOf(tokenAppId))) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
		}
		return new SocialIdentity(SocialProvider.KAKAO, String.valueOf(id));
	}

}
