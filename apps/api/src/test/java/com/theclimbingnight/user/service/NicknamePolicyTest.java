package com.theclimbingnight.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import org.junit.jupiter.api.Test;

class NicknamePolicyTest {

	private final NicknamePolicy policy = new NicknamePolicy();

	private void assertInvalid(String raw) {
		assertThatThrownBy(() -> policy.normalize(raw))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NICKNAME_INVALID));
	}

	@Test
	void 닉네임_규칙_경계값() {
		// given, when, then
		assertInvalid("가");
		assertThat(policy.normalize("가나")).isEqualTo("가나");
		assertThat(policy.normalize("a".repeat(16))).hasSize(16);
		assertInvalid("a".repeat(17));
		assertThat(policy.normalize("  홍길동 ")).isEqualTo("홍길동");
		assertInvalid("클라이머🧗");
		assertInvalid("a b");
		assertThat(policy.normalize("climb_er1")).isEqualTo("climb_er1");
	}

	@Test
	void 닉네임이_null이면_INVALID다() {
		// given, when, then
		assertInvalid(null);
	}

}
