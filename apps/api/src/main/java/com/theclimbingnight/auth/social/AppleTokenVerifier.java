package com.theclimbingnight.auth.social;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.theclimbingnight.auth.AuthProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AppleTokenVerifier implements SocialTokenVerifier {

	private final IdTokenVerifier idTokenVerifier;

	public AppleTokenVerifier(@Qualifier("appleJwkSource") JWKSource<SecurityContext> jwkSource,
			AuthProperties properties, Clock clock) {
		this.idTokenVerifier = new IdTokenVerifier(jwkSource, List.of("https://appleid.apple.com"),
				properties.apple().audiences(), clock);
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.APPLE;
	}

	@Override
	public SocialIdentity verify(String token) {
		return new SocialIdentity(SocialProvider.APPLE, idTokenVerifier.verifyAndGetSubject(token));
	}

}
