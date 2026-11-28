package com.theclimbingnight.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import org.junit.jupiter.api.Test;

class BioPolicyTest {

	private final BioPolicy policy = new BioPolicy();

	@Test
	void 공백만_있으면_null이다() {
		assertThat(policy.normalize("  ")).isNull();
		assertThat(policy.normalize(null)).isNull();
	}

	@Test
	void 줄바꿈은_LF로_통일하고_앞뒤_공백을_자른다() {
		assertThat(policy.normalize(" a\r\nb\rc ")).isEqualTo("a\nb\nc");
	}

	@Test
	void 코드포인트_150자까지_통과하고_151자는_거부한다() {
		assertThat(policy.normalize("a".repeat(150))).hasSize(150);
		assertThatThrownBy(() -> policy.normalize("a".repeat(151))).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_BIO_INVALID));
	}

	@Test
	void 이모지_150개는_통과한다() {
		String emojis = "😀".repeat(150);
		assertThat(policy.normalize(emojis)).isEqualTo(emojis);
	}

	@Test
	void 줄바꿈은_3개까지만_된다() {
		assertThat(policy.normalize("1\n2\n3\n4")).isEqualTo("1\n2\n3\n4");
		assertThatThrownBy(() -> policy.normalize("1\n2\n3\n4\n5")).isInstanceOf(BusinessException.class);
	}
}
