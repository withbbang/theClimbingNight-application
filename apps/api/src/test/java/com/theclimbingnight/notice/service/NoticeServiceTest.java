package com.theclimbingnight.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.notice.dto.NoticeRequest;
import com.theclimbingnight.notice.dto.NoticeResponse;
import com.theclimbingnight.notice.entity.Notice;
import com.theclimbingnight.notice.repository.NoticeRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoticeServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final long ADMIN_ID = 1L;

	@Mock NoticeRepository noticeRepository;
	@Mock UserRepository userRepository;
	@Mock ApplicationEventPublisher eventPublisher;

	private MutableClock clock;
	private NoticeService service;
	private User admin;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW);
		service = new NoticeService(noticeRepository, userRepository, eventPublisher, clock);
		admin = User.create("admin");
		ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
		given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
		given(noticeRepository.saveAndFlush(any(Notice.class))).willAnswer(call -> {
			Notice notice = call.getArgument(0);
			ReflectionTestUtils.setField(notice, "id", 5L);
			return notice;
		});
	}

	@Test
	void 작성하면_이벤트를_발행한다() {
		// given
		NoticeRequest request = new NoticeRequest("  새 공지  ", "# 본문\n\n내용 ", true);

		// when
		NoticeResponse response = service.create(ADMIN_ID, request);

		// then
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new NoticePublishedEvent(5L, ADMIN_ID, "새 공지"));
		assertThat(response.title()).isEqualTo("새 공지");
		assertThat(response.body()).isEqualTo("# 본문\n\n내용 ");
		assertThat(response.pinned()).isTrue();
		assertThat(response.publishedAt()).isEqualTo(NOW);
	}

	@Test
	void 수정은_게시_시각을_바꾸지_않고_이벤트도_없다() {
		// given
		Notice notice = Notice.create("옛 제목", "옛 본문", false, admin, NOW);
		ReflectionTestUtils.setField(notice, "id", 5L);
		given(noticeRepository.findById(5L)).willReturn(Optional.of(notice));
		clock.advance(java.time.Duration.ofHours(1));

		// when
		NoticeResponse response = service.update(5L, new NoticeRequest(" 새 제목 ", "새 본문", true));

		// then
		assertThat(response.title()).isEqualTo("새 제목");
		assertThat(response.body()).isEqualTo("새 본문");
		assertThat(response.pinned()).isTrue();
		assertThat(response.publishedAt()).isEqualTo(NOW);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void 없는_공지를_수정하거나_지우면_404다() {
		// given
		given(noticeRepository.findById(9L)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.update(9L, new NoticeRequest("t", "b", false)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThatThrownBy(() -> service.delete(9L)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}
}
