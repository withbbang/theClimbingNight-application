package com.theclimbingnight.media.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;

/**
 * 비공개 버킷(uploads/·quarantine/)과 공개 버킷을 다루는 저장소.
 * 파일은 스트림이나 Path로만 주고받는다.
 */
public interface MediaStorage {

	PresignedUpload presignUpload(String key, String contentType, long byteSize, Duration ttl);

	/** 비공개 버킷 HEAD. 없으면 empty. */
	OptionalLong uploadedSize(String key);

	/** 호출한 쪽이 닫는다. */
	InputStream openUpload(String key);

	void deleteUpload(String key);

	/** Cache-Control: public, max-age=31536000, immutable 로 올린다. */
	void putPublic(String key, Path file, String contentType);

	void deletePublic(String key);

	/** 공개본을 비공개 버킷의 quarantine/{publicKey}로 복사한다. */
	void copyPublicToQuarantine(String publicKey);

	/** quarantine/ 아래 키를 새 공개 키로 복사한다. quarantineKey는 quarantine/ 접두사 없는 공개 키. */
	void copyQuarantineToPublic(String quarantineKey, String newPublicKey);

	void deleteQuarantine(String publicKey);

	/** quarantine/{publicKey}가 있는지 HEAD로 본다. */
	boolean quarantineExists(String publicKey);

	URI presignQuarantineGet(String publicKey, Duration ttl);
}
