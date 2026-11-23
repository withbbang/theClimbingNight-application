package com.theclimbingnight.auth.social;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import org.springframework.web.client.RestClientResponseException;

final class ProviderErrors {

	private ProviderErrors() {
	}

	/** 제공자가 4xx로 응답하면 토큰 문제, 그 밖(5xx 등)이면 제공자 장애로 본다. */
	static BusinessException from(RestClientResponseException e) {
		return e.getStatusCode().is4xxClientError()
				? new BusinessException(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN)
				: new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
	}

}
