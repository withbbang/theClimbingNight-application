package com.theclimbingnight.media.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;

/** R2 키가 비어 있을 때 쓴다. 모든 호출이 MEDIA_UNAVAILABLE로 거절된다. */
public class DisabledMediaStorage implements MediaStorage {

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long byteSize, Duration ttl) {
		throw unavailable();
	}

	@Override
	public OptionalLong uploadedSize(String key) {
		throw unavailable();
	}

	@Override
	public InputStream openUpload(String key) {
		throw unavailable();
	}

	@Override
	public void deleteUpload(String key) {
		throw unavailable();
	}

	@Override
	public void putPublic(String key, Path file, String contentType) {
		throw unavailable();
	}

	@Override
	public void deletePublic(String key) {
		throw unavailable();
	}

	@Override
	public void copyPublicToQuarantine(String publicKey) {
		throw unavailable();
	}

	@Override
	public void copyQuarantineToPublic(String quarantineKey, String newPublicKey) {
		throw unavailable();
	}

	@Override
	public void deleteQuarantine(String publicKey) {
		throw unavailable();
	}

	@Override
	public boolean quarantineExists(String publicKey) {
		throw unavailable();
	}

	@Override
	public URI presignQuarantineGet(String publicKey, Duration ttl) {
		throw unavailable();
	}

	private static BusinessException unavailable() {
		return new BusinessException(ErrorCode.MEDIA_UNAVAILABLE);
	}
}
