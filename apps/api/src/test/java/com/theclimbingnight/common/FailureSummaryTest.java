package com.theclimbingnight.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.push.expo.PushTransientException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FailureSummaryTest {

	@Test
	void SQL_예외가_원인이면_상태와_오류_번호를_붙이고_메시지는_뺀다() {
		// given
		RuntimeException e = new DataIntegrityViolationException("secret",
				new RuntimeException(new SQLException("secret", "23000", 1062)));

		// when, then
		assertThat(FailureSummary.of(e))
				.isEqualTo("DataIntegrityViolationException sqlState=23000 errorCode=1062");
	}

	@Test
	void SQL_예외가_없으면_클래스_이름만_남긴다() {
		assertThat(FailureSummary.of(new IllegalStateException("secret"))).isEqualTo("IllegalStateException");
	}

	@Test
	void 푸시_예외도_클래스_이름만_남긴다() {
		assertThat(FailureSummary.of(new PushTransientException("HTTP_503"))).isEqualTo("PushTransientException");
	}

	@Test
	void SQL_상태가_없어도_오류_번호를_남긴다() {
		assertThat(FailureSummary.of(new RuntimeException(new SQLException("secret", null, 7))))
				.isEqualTo("RuntimeException sqlState=null errorCode=7");
	}

	@Test
	void 원인이_순환해도_끝난다() {
		// given
		RuntimeException a = new RuntimeException("a");
		RuntimeException b = new RuntimeException("b", a);
		a.initCause(b);

		// when, then
		assertThat(FailureSummary.of(a)).isEqualTo("RuntimeException");
	}
}
