package com.theclimbingnight.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CursorTest {

	@Test
	void 인코딩한_커서는_그대로_돌아온다() {
		// given
		Instant publishedAt = Instant.parse("2026-11-26T12:00:00.123456Z");

		// when
		String encoded = Cursor.encode(publishedAt, 42L);

		// then
		Cursor decoded = Cursor.decode(encoded).orElseThrow();
		assertThat(decoded.publishedAt()).isEqualTo(publishedAt);
		assertThat(decoded.id()).isEqualTo(42L);
		assertThat(encoded).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void 값이_없으면_빈_결과다() {
		assertThat(Cursor.decode(null)).isEmpty();
		assertThat(Cursor.decode("")).isEmpty();
	}

	@Test
	void 깨진_커서는_검증_실패다() {
		for (String broken : new String[] {"!!!", "bm90LWEtY3Vyc29y", "MTo", "YTpi"}) {
			assertThatThrownBy(() -> Cursor.decode(broken)).isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
				assertThat(e.getFields()).extracting("field").containsExactly("cursor");
			});
		}
	}
}
