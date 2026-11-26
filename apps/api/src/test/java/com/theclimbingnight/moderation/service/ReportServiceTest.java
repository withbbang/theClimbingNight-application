package com.theclimbingnight.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.moderation.dto.ReportCreateRequest;
import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

	private static final long REPORTER = 3L;
	private static final long AUTHOR = 7L;
	private static final long POST_ID = 50L;

	@Mock ReportRepository reports;
	@Mock PostRepository posts;
	@Mock UserRepository users;
	@Mock UserBlockRepository blocks;
	@Mock MediaJobScheduler scheduler;
	@Mock ApplicationEventPublisher events;

	private ReportService service;

	@BeforeEach
	void setUp() {
		service = new ReportService(reports, posts, users, blocks, scheduler, events);
	}

	private Post givenPost(PostStatus status) {
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		given(posts.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
		return post;
	}

	private void givenOpenCounts(Long first, Long... rest) {
		given(reports.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, POST_ID, ReportStatus.OPEN))
				.willReturn(first, rest);
	}

	private static ReportCreateRequest postReport(ReportReason reason) {
		return new ReportCreateRequest(ReportTargetType.POST, POST_ID, reason, null);
	}

	@Test
	void 초상권_신고는_1건으로_자동_숨김되고_격리_작업이_생긴다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		givenOpenCounts(0L);

		// when
		service.submit(REPORTER, postReport(ReportReason.PORTRAIT_PRIVACY));

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN_PENDING);
		verify(scheduler).onPostHidden(post);
		ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
		verify(reports).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getReporterId()).isEqualTo(REPORTER);
		assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.OPEN);
	}

	@Test
	void 처리_중인_글도_초상권_신고로_숨김된다() {
		// given
		Post post = givenPost(PostStatus.PENDING);
		givenOpenCounts(0L);

		// when
		service.submit(REPORTER, postReport(ReportReason.PORTRAIT_PRIVACY));

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN_PENDING);
		verify(scheduler).onPostHidden(post);
	}

	@Test
	void 다른_사유는_서로_다른_3명째에_숨김된다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		givenOpenCounts(0L, 1L, 2L);

		// when
		service.submit(1L, postReport(ReportReason.SPAM));
		service.submit(2L, postReport(ReportReason.ABUSE));

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		verify(scheduler, never()).onPostHidden(any());

		// when
		service.submit(4L, postReport(ReportReason.SEXUAL));

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN_PENDING);
		verify(scheduler, times(1)).onPostHidden(post);
	}

	@Test
	void 이미_숨긴_글에_신고가_더_와도_격리_작업을_다시_만들지_않는다() {
		// given
		Post pending = givenPost(PostStatus.HIDDEN_PENDING);
		givenOpenCounts(3L);

		// when
		service.submit(REPORTER, postReport(ReportReason.PORTRAIT_PRIVACY));

		// then
		assertThat(pending.getStatus()).isEqualTo(PostStatus.HIDDEN_PENDING);
		verify(scheduler, never()).onPostHidden(any());
	}

	@Test
	void 운영진이_숨긴_글은_신고가_더_와도_상태가_그대로다() {
		// given
		Post hidden = givenPost(PostStatus.HIDDEN);
		givenOpenCounts(5L);

		// when
		service.submit(REPORTER, postReport(ReportReason.SPAM));

		// then
		assertThat(hidden.getStatus()).isEqualTo(PostStatus.HIDDEN);
		verify(scheduler, never()).onPostHidden(any());
	}

	@Test
	void 같은_대상_두번째_신고는_409다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		givenOpenCounts(1L);
		given(reports.saveAndFlush(any(Report.class))).willThrow(new DataIntegrityViolationException(
				"Unique index or primary key violation: \"PUBLIC.UK_REPORTS_REPORTER_TARGET_INDEX_8\""));

		// when / then
		assertThatThrownBy(() -> service.submit(REPORTER, postReport(ReportReason.PORTRAIT_PRIVACY)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_ALREADY_SUBMITTED));
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		verify(scheduler, never()).onPostHidden(any());
		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void 다른_제약_위반은_그대로_던진다() {
		// given
		givenPost(PostStatus.VISIBLE);
		givenOpenCounts(0L);
		DataIntegrityViolationException fk = new DataIntegrityViolationException("fk_reports_reporter");
		given(reports.saveAndFlush(any(Report.class))).willThrow(fk);

		// when / then
		assertThatThrownBy(() -> service.submit(REPORTER, postReport(ReportReason.SPAM))).isSameAs(fk);
	}

	@Test
	void 운영진_푸시는_대상의_첫_신고에만_발행된다() {
		// given
		givenPost(PostStatus.VISIBLE);
		givenOpenCounts(0L, 1L);

		// when
		service.submit(1L, postReport(ReportReason.SPAM));
		service.submit(2L, postReport(ReportReason.SPAM));

		// then
		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(events, times(1)).publishEvent(published.capture());
		assertThat(published.getValue()).isEqualTo(new ReportSubmittedEvent(ReportTargetType.POST, POST_ID, 1L));
	}

	@Test
	void 내_글은_신고할_수_없다() {
		// given
		givenPost(PostStatus.VISIBLE);

		// when / then
		assertThatThrownBy(() -> service.submit(AUTHOR, postReport(ReportReason.SPAM)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_SELF));
		verify(reports, never()).saveAndFlush(any());
	}

	@Test
	void 차단_관계의_글이나_초안이나_지운_글은_404다() {
		// given
		givenPost(PostStatus.VISIBLE);
		given(blocks.existsBetween(REPORTER, AUTHOR)).willReturn(true);

		// when / then
		assertNotFound(() -> service.submit(REPORTER, postReport(ReportReason.SPAM)));

		// given
		givenPost(PostStatus.DRAFT);

		// when / then
		assertNotFound(() -> service.submit(REPORTER, postReport(ReportReason.SPAM)));

		// given
		Post deleted = givenPost(PostStatus.VISIBLE);
		deleted.softDelete(Instant.parse("2026-11-26T00:00:00Z"));

		// when / then
		assertNotFound(() -> service.submit(REPORTER, postReport(ReportReason.SPAM)));
		verify(reports, never()).saveAndFlush(any());
	}

	@Test
	void 사용자_신고는_숨김_없이_저장되고_첫_신고면_알린다() {
		// given
		User target = User.create("target");
		ReflectionTestUtils.setField(target, "id", 9L);
		given(users.findByIdForUpdate(9L)).willReturn(Optional.of(target));
		given(reports.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.USER, 9L, ReportStatus.OPEN))
				.willReturn(0L);

		// when
		service.submit(REPORTER, new ReportCreateRequest(ReportTargetType.USER, 9L, ReportReason.OTHER, "  욕설 DM  "));

		// then
		ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
		verify(reports).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getDetail()).isEqualTo("욕설 DM");
		verify(events).publishEvent((Object) new ReportSubmittedEvent(ReportTargetType.USER, 9L, REPORTER));
		verify(scheduler, never()).onPostHidden(any());
	}

	@Test
	void 나_자신을_신고하면_REPORT_SELF고_탈퇴한_사용자는_404다() {
		// when / then
		assertThatThrownBy(() -> service.submit(REPORTER,
				new ReportCreateRequest(ReportTargetType.USER, REPORTER, ReportReason.SPAM, null)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_SELF));

		// given
		User left = User.create("left");
		left.withdraw(Instant.parse("2026-11-26T00:00:00Z"));
		given(users.findByIdForUpdate(9L)).willReturn(Optional.of(left));

		// when / then
		assertNotFound(() -> service.submit(REPORTER,
				new ReportCreateRequest(ReportTargetType.USER, 9L, ReportReason.SPAM, null)));
		verify(reports, never()).saveAndFlush(any());
	}

	private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}
}
