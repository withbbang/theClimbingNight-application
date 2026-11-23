package com.theclimbingnight.auth.social;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SocialTokenVerifiers {

	private final Map<SocialProvider, SocialTokenVerifier> verifiers = new EnumMap<>(SocialProvider.class);

	public SocialTokenVerifiers(List<SocialTokenVerifier> all) {
		all.forEach(v -> verifiers.put(v.provider(), v));
	}

	public SocialTokenVerifier get(SocialProvider provider) {
		SocialTokenVerifier verifier = verifiers.get(provider);
		if (verifier == null) {
			throw new IllegalStateException("검증기가 없는 제공자입니다: " + provider);
		}
		return verifier;
	}

}
