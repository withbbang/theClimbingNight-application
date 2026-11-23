package com.theclimbingnight.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokens {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom;

	public RefreshTokens(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	/** 무작위 32바이트를 패딩 없는 base64url로 인코딩한 새 리프레시 토큰. */
	public String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** DB에는 원문이 아니라 이 SHA-256 hex(64자)만 저장한다. */
	public static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}

}
