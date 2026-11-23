package com.theclimbingnight.auth.social;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.theclimbingnight.auth.AuthProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

	private final IdTokenVerifier idTokenVerifier;

	public GoogleTokenVerifier(@Qualifier("googleJwkSource") JWKSource<SecurityContext> jwkSource,
			AuthProperties properties, Clock clock) {
		this.idTokenVerifier = new IdTokenVerifier(jwkSource,
				List.of("accounts.google.com", "https://accounts.google.com"),
				properties.google().clientIds(), clock);
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public SocialIdentity verify(String token) {
		return new SocialIdentity(SocialProvider.GOOGLE, idTokenVerifier.verifyAndGetSubject(token));
	}

}
