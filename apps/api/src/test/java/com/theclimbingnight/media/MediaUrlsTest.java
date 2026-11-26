package com.theclimbingnight.media;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class MediaUrlsTest {

	@Test
	void 기본_주소_끝의_슬래시와_무관하게_한_번만_붙는다() {
		assertThat(urls("https://media.x/").publicUrl("p/u/d.webp")).isEqualTo("https://media.x/p/u/d.webp");
		assertThat(urls("https://media.x").publicUrl("p/u/d.webp")).isEqualTo("https://media.x/p/u/d.webp");
	}

	private static MediaUrls urls(String baseUrl) {
		return new MediaUrls(new MediaProperties(true, "a", "b", "c", "in", "pub", baseUrl, null));
	}
}
