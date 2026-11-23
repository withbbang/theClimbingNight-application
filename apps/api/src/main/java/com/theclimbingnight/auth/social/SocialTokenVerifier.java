package com.theclimbingnight.auth.social;

public interface SocialTokenVerifier {

	SocialProvider provider();

	/** 소셜 토큰을 검증하고 제공자의 사용자 ID를 돌려준다. */
	SocialIdentity verify(String token);

}
