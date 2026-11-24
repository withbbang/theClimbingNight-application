package com.theclimbingnight.push.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.repository.PushTokenRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

	private static final String TOKEN = "ExponentPushToken[abc]";

	@Mock PushTokenWriter writer;
	@Mock PushTokenRepository pushTokenRepository;
	@InjectMocks PushTokenService service;

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("could not execute statement",
				new ConstraintViolationException("dup", new java.sql.SQLException("dup"), constraint));
	}

	@Test
	void 동시_등록으로_유니크_위반이면_한_번_다시_시도한다() {
		// given
		willThrow(violation("uk_push_tokens_token")).willDoNothing().given(writer).upsert(1L, TOKEN, PushPlatform.IOS);

		// when
		service.register(1L, TOKEN, PushPlatform.IOS);

		// then
		verify(writer, times(2)).upsert(1L, TOKEN, PushPlatform.IOS);
	}

	@Test
	void 두_번째도_위반이면_그대로_던진다() {
		// given
		willThrow(violation("uk_push_tokens_token")).given(writer).upsert(1L, TOKEN, PushPlatform.IOS);

		// when, then
		assertThatThrownBy(() -> service.register(1L, TOKEN, PushPlatform.IOS))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(writer, times(2)).upsert(1L, TOKEN, PushPlatform.IOS);
	}

	@Test
	void 다른_제약_위반은_그대로_던진다() {
		// given
		willThrow(violation("fk_push_tokens_user")).given(writer).upsert(1L, TOKEN, PushPlatform.IOS);

		// when, then
		assertThatThrownBy(() -> service.register(1L, TOKEN, PushPlatform.IOS))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(writer, times(1)).upsert(1L, TOKEN, PushPlatform.IOS);
	}

	@Test
	void 내_토큰만_지운다() {
		// given, when
		service.deleteOwned(1L, TOKEN);

		// then
		verify(pushTokenRepository).deleteByUserIdAndToken(1L, TOKEN);
	}
}
