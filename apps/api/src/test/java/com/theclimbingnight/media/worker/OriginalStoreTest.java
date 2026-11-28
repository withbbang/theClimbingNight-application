package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OriginalStoreTest {

	@TempDir Path root;

	private OriginalStore store() {
		return new OriginalStore(root);
	}

	@Test
	void 연_월_폴더에_저장하고_상대_경로를_준다() throws Exception {
		// when
		String path = store().save(11L, new ByteArrayInputStream(new byte[] {1, 2, 3}),
				Instant.parse("2026-11-26T12:00:00Z"));

		// then
		assertThat(path).isEqualTo("2026/11/11.jpg");
		assertThat(Files.readAllBytes(root.resolve("2026/11/11.jpg"))).containsExactly(1, 2, 3);
	}

	@Test
	void 연_월은_한국_시각으로_정한다() throws Exception {
		// when: UTC로는 11월 30일 15시, 한국은 12월 1일 0시
		String path = store().save(12L, new ByteArrayInputStream(new byte[] {1}), Instant.parse("2026-11-30T15:00:00Z"));

		// then
		assertThat(path).isEqualTo("2026/12/12.jpg");
	}

	@Test
	void 저장한_원본을_열고_지울_수_있다() throws Exception {
		// given
		OriginalStore store = store();
		String path = store.save(11L, new ByteArrayInputStream(new byte[] {7}), Instant.parse("2026-11-26T12:00:00Z"));

		// when
		byte[] read;
		try (InputStream in = store.open(path)) {
			read = in.readAllBytes();
		}
		store.delete(path);

		// then
		assertThat(read).containsExactly(7);
		assertThat(store.exists(path)).isFalse();
	}

	@Test
	void 없는_원본을_지워도_성공한다() throws Exception {
		// when / then
		store().delete("2026/11/404.jpg");
	}

	@Test
	void 루트_밖_경로는_거부한다() {
		// given
		OriginalStore store = store();

		// when / then
		assertThatThrownBy(() -> store.open("../outside.jpg")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> store.delete("2026/../../outside.jpg")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> store.exists("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 프로필_원본은_profile_폴더_아래_연_월_폴더에_저장한다() throws Exception {
		// when: UTC로는 11월 30일 15시, 한국은 12월 1일 0시
		String path = store().saveProfile(11L, new ByteArrayInputStream(new byte[] {4, 5}),
				Instant.parse("2026-11-30T15:00:00Z"));

		// then
		assertThat(path).isEqualTo("profile/2026/12/11.jpg");
		assertThat(Files.readAllBytes(root.resolve("profile/2026/12/11.jpg"))).containsExactly(4, 5);
		try (var files = Files.list(root.resolve("profile/2026/12"))) {
			assertThat(files).containsExactly(root.resolve("profile/2026/12/11.jpg"));
		}
	}
}
