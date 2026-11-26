package com.theclimbingnight.media.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

public class R2MediaStorage implements MediaStorage {

	static final String QUARANTINE_PREFIX = "quarantine/";
	static final String PUBLIC_CACHE_CONTROL = "public, max-age=31536000, immutable";

	private final S3Client s3;
	private final S3Presigner presigner;
	private final String incomingBucket;
	private final String publicBucket;

	public R2MediaStorage(S3Client s3, S3Presigner presigner, String incomingBucket, String publicBucket) {
		this.s3 = s3;
		this.presigner = presigner;
		this.incomingBucket = incomingBucket;
		this.publicBucket = publicBucket;
	}

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long byteSize, Duration ttl) {
		PutObjectRequest put = PutObjectRequest.builder()
				.bucket(incomingBucket).key(key).contentType(contentType).contentLength(byteSize).build();
		PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r
				.signatureDuration(ttl).putObjectRequest(put));
		// host는 HTTP 클라이언트가 채운다. 앱이 보내야 하는 헤더만 돌려준다.
		Map<String, String> headers = new LinkedHashMap<>();
		presigned.signedHeaders().forEach((name, values) -> {
			if (!name.equalsIgnoreCase("host") && !values.isEmpty()) {
				headers.put(canonicalName(name), values.get(0));
			}
		});
		return new PresignedUpload(presigned.url().toString(), Map.copyOf(headers), presigned.expiration());
	}

	@Override
	public OptionalLong uploadedSize(String key) {
		return headSize(incomingBucket, key);
	}

	@Override
	public boolean publicExists(String key) {
		return headSize(publicBucket, key).isPresent();
	}

	@Override
	public boolean quarantineExists(String publicKey) {
		return headSize(incomingBucket, QUARANTINE_PREFIX + publicKey).isPresent();
	}

	private OptionalLong headSize(String bucket, String key) {
		try {
			return OptionalLong.of(s3.headObject(HeadObjectRequest.builder()
					.bucket(bucket).key(key).build()).contentLength());
		} catch (NoSuchKeyException e) {
			return OptionalLong.empty();
		} catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
			if (e.statusCode() == 404) {
				return OptionalLong.empty();
			}
			throw e;
		}
	}

	@Override
	public InputStream openUpload(String key) {
		return s3.getObject(GetObjectRequest.builder().bucket(incomingBucket).key(key).build());
	}

	@Override
	public void deleteUpload(String key) {
		delete(incomingBucket, key);
	}

	@Override
	public void putPublic(String key, Path file, String contentType) {
		s3.putObject(PutObjectRequest.builder()
				.bucket(publicBucket).key(key).contentType(contentType).cacheControl(PUBLIC_CACHE_CONTROL).build(),
				RequestBody.fromFile(file));
	}

	@Override
	public void deletePublic(String key) {
		delete(publicBucket, key);
	}

	@Override
	public void copyPublicToQuarantine(String publicKey) {
		copy(publicBucket, publicKey, incomingBucket, QUARANTINE_PREFIX + publicKey);
	}

	@Override
	public void copyQuarantineToPublic(String quarantineKey, String newPublicKey) {
		copy(incomingBucket, QUARANTINE_PREFIX + quarantineKey, publicBucket, newPublicKey);
	}

	@Override
	public void deleteQuarantine(String publicKey) {
		delete(incomingBucket, QUARANTINE_PREFIX + publicKey);
	}

	@Override
	public URI presignQuarantineGet(String publicKey, Duration ttl) {
		GetObjectRequest get = GetObjectRequest.builder()
				.bucket(incomingBucket).key(QUARANTINE_PREFIX + publicKey).build();
		try {
			return presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(get)).url().toURI();
		} catch (java.net.URISyntaxException e) {
			throw new IllegalStateException("presigned URL 형식 오류", e);
		}
	}

	private void copy(String srcBucket, String srcKey, String dstBucket, String dstKey) {
		s3.copyObject(CopyObjectRequest.builder()
				.sourceBucket(srcBucket).sourceKey(srcKey).destinationBucket(dstBucket).destinationKey(dstKey).build());
	}

	private void delete(String bucket, String key) {
		s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
	}

	/** SDK는 서명 헤더 이름을 소문자로 돌려준다. 앱이 보내는 이름은 관례대로 대문자 시작으로 맞춘다. */
	private static String canonicalName(String name) {
		StringBuilder out = new StringBuilder();
		boolean upper = true;
		for (char c : name.toCharArray()) {
			out.append(upper ? Character.toUpperCase(c) : c);
			upper = c == '-';
		}
		return out.toString();
	}
}
