package com.theclimbingnight.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.inquiry.dto.InquiryAdminResponse;
import com.theclimbingnight.inquiry.dto.InquiryAnswerRequest;
import com.theclimbingnight.inquiry.dto.InquiryCreateRequest;
import com.theclimbingnight.inquiry.dto.InquiryResponse;
import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import com.theclimbingnight.inquiry.repository.InquiryRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InquiryServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final long OWNER_ID = 10L;
	private static final long ADMIN_ID = 1L;

	@Mock InquiryRepository inquiryRepository;
	@Mock UserRepository userRepository;
	@Mock ApplicationEventPublisher eventPublisher;

	private MutableClock clock;
	private InquiryService service;
	private User owner;
	private User admin;
	private Inquiry inquiry;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW);
		service = new InquiryService(inquiryRepository, userRepository, eventPublisher, clock);
		owner = User.create("owner");
		ReflectionTestUtils.setField(owner, "id", OWNER_ID);
		admin = User.create("admin");
		ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
		inquiry = Inquiry.create(owner, "제목", "본문");
		ReflectionTestUtils.setField(inquiry, "id", 5L);
		given(userRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner));
		given(userRepository.findByIdForUpdate(ADMIN_ID)).willReturn(Optional.of(admin));
		given(inquiryRepository.findById(5L)).willReturn(Optional.of(inquiry));
		given(inquiryRepository.findByIdForUpdate(5L)).willReturn(Optional.of(inquiry));
		given(inquiryRepository.findWithUserById(5L)).willReturn(Optional.of(inquiry));
	}

	@Test
	void 탈퇴한_회원은_문의를_남길_수_없다() {
		// given: 요청이 들어온 뒤 탈퇴가 먼저 끝났다.
		owner.withdraw(NOW);
		given(userRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner));

		// when, then
		assertThatThrownBy(() -> service.create(OWNER_ID, new InquiryCreateRequest("제목", "본문")))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(inquiryRepository, Mockito.never()).saveAndFlush(any());
	}

	@Test
	void 남의_문의를_조회하면_404다() {
		// when, then
		assertThatThrownBy(() -> service.getMine(99L, 5L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 내_문의는_조회된다() {
		// when
		InquiryResponse response = service.getMine(OWNER_ID, 5L);

		// then
		assertThat(response.id()).isEqualTo(5L);
		assertThat(response.status()).isEqualTo(InquiryStatus.OPEN);
	}

	@Test
	void 첫_답변에만_이벤트를_발행한다() {
		// when
		service.answer(5L, ADMIN_ID, new InquiryAnswerRequest("첫 답변"));
		clock.advance(Duration.ofMinutes(5));
		InquiryAdminResponse response = service.answer(5L, ADMIN_ID, new InquiryAnswerRequest("고친 답변"));

		// then
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(1)).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new InquiryAnsweredEvent(5L, OWNER_ID));
		assertThat(response.answer()).isEqualTo("고친 답변");
		assertThat(response.status()).isEqualTo(InquiryStatus.ANSWERED);
		assertThat(response.answeredAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
	}

	@Test
	void 답변은_운영진_행을_문의_행보다_먼저_잠근다() {
		// when
		service.answer(5L, ADMIN_ID, new InquiryAnswerRequest("답변"));

		// then
		InOrder order = Mockito.inOrder(userRepository, inquiryRepository);
		order.verify(userRepository).findByIdForUpdate(ADMIN_ID);
		order.verify(inquiryRepository).findByIdForUpdate(5L);
	}

	@Test
	void 없는_운영진으로_답변하면_401이다() {
		// given
		given(userRepository.findByIdForUpdate(77L)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.answer(5L, 77L, new InquiryAnswerRequest("답변")))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
		verify(eventPublisher, Mockito.never()).publishEvent(any(Object.class));
	}

	@Test
	void 없는_문의에_답변하면_404다() {
		// given
		given(inquiryRepository.findByIdForUpdate(9L)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.answer(9L, ADMIN_ID, new InquiryAnswerRequest("답변")))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 탈퇴한_문의자의_닉네임은_null이다() {
		// given
		owner.withdraw(NOW);

		// when
		InquiryAdminResponse response = service.get(5L);

		// then
		assertThat(response.nickname()).isNull();
		assertThat(response.userId()).isEqualTo(OWNER_ID);
	}

	@Test
	void 문의하면_열린_상태로_저장한다() {
		// given
		given(inquiryRepository.saveAndFlush(any(Inquiry.class))).willAnswer(call -> {
			Inquiry saved = call.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 6L);
			return saved;
		});

		// when
		InquiryResponse response = service.create(OWNER_ID,
				new com.theclimbingnight.inquiry.dto.InquiryCreateRequest("  제목  ", "본문"));

		// then
		assertThat(response.id()).isEqualTo(6L);
		assertThat(response.title()).isEqualTo("제목");
		assertThat(response.status()).isEqualTo(InquiryStatus.OPEN);
		assertThat(response.answer()).isNull();
	}
}
