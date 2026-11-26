package com.theclimbingnight.media.worker;

import com.theclimbingnight.common.TimeZones;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * NAS의 원본 사진 폴더. DB에는 루트 기준 상대 경로 {@code yyyy/MM/{postMediaId}.jpg}만 둔다.
 * 상대 경로가 루트 밖을 가리키면({@code ..}, 절대 경로) 거부한다.
 */
public class OriginalStore {

	private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyy/MM").withZone(TimeZones.KST);

	private final Path root;

	public OriginalStore(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	/** 같은 폴더의 임시 파일에 다 쓴 뒤 옮겨서, 중간에 끊겨도 반쯤 쓴 원본이 남지 않게 한다. */
	public String save(long mediaId, InputStream in, Instant now) throws IOException {
		String relative = MONTH_DIR.format(now) + "/" + mediaId + ".jpg";
		Path target = resolve(relative);
		Files.createDirectories(target.getParent());
		Path temp = Files.createTempFile(target.getParent(), mediaId + "-", ".part");
		try {
			Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temp);
		}
		return relative;
	}

	/** 호출한 쪽이 닫는다. */
	public InputStream open(String relativePath) throws IOException {
		return Files.newInputStream(resolve(relativePath));
	}

	public boolean exists(String relativePath) {
		return Files.isRegularFile(resolve(relativePath));
	}

	/** 없어도 성공한다. */
	public void delete(String relativePath) throws IOException {
		Files.deleteIfExists(resolve(relativePath));
	}

	private Path resolve(String relativePath) {
		Path relative = Path.of(relativePath);
		Path resolved = root.resolve(relative).normalize();
		if (relative.isAbsolute() || !resolved.startsWith(root) || resolved.equals(root)) {
			throw new IllegalArgumentException("원본 경로가 루트 밖입니다");
		}
		return resolved;
	}
}
