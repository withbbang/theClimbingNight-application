package com.theclimbingnight.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.entity.CommentStatus;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.service.CommentCounter;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.feed.service.PostResponseMapper;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.moderation.dto.ReportTargetActionRequest.Action;
import com.theclimbingnight.moderation.dto.ReportTargetResponse;
import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.moderation.repository.ReportTargetSummary;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
	private static final long ADMIN = 1L;
	private static final long AUTHOR = 7L;
	private static final long POST_ID = 50L;
	private static final long COMMENT_ID = 80L;

	@Mock ReportRepository reports;
	@Mock PostRepository posts;
	@Mock PostMediaRepository mediaRepository;
	@Mock CommentRepository comments;
	@Mock CommentCounter counter;
	@Mock UserRepository users;
	@Mock MediaJobRepository jobs;
	@Mock MediaStorage storage;

	private final MutableClock clock = new MutableClock(NOW);
	private ModerationService service;

	@BeforeEach
	void setUp() {
		MediaUrls urls = new MediaUrls(new MediaProperties(true, "a", "k", "s", "in", "pub", "https://m.example", null));
		MediaJobScheduler scheduler = new MediaJobScheduler(jobs, mediaRepository, clock);
		service = new ModerationService(reports, posts, comments, counter, mediaRepository, users, jobs, scheduler, storage,
				new PostResponseMapper(urls), urls, clock);
	}

	private Post givenPost(PostStatus status) {
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		given(posts.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
		return post;
	}

	private List<PostMedia> givenMedia() {
		List<PostMedia> media = List.of(media(11L, 0), media(12L, 1));
		given(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID)).willReturn(media);
		return media;
	}

	private static PostMedia media(long id, int order) {
		PostMedia media = PostMedia.image(POST_ID, order, "uploads/u" + id + ".jpg", "p/k" + id);
		ReflectionTestUtils.setField(media, "id", id);
		return media;
	}

	private Report givenOpenReport(ReportTargetType type, long targetId) {
		Report report = Report.open(3L, type, targetId, ReportReason.SPAM, null);
		given(reports.findAllByTargetTypeAndTargetIdAndStatus(type, targetId, ReportStatus.OPEN))
				.willReturn(List.of(report));
		return report;
	}

	@SuppressWarnings("unchecked")
	private List<MediaJob> savedJobs() {
		ArgumentCaptor<List<MediaJob>> captor = ArgumentCaptor.forClass(List.class);
		verify(jobs).saveAll(captor.capture());
		return captor.getValue();
	}

	@Test
	void 숨기면_HIDDEN이_되고_사진마다_격리_작업이_생기고_신고는_ACTIONED다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		givenMedia();
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.HIDE);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
		verify(jobs).cancelQueued(List.of(11L, 12L), MediaJobKind.PROCESS);
		assertThat(savedJobs()).extracting(MediaJob::getKind, MediaJob::getTargetId).containsExactly(
				org.assertj.core.groups.Tuple.tuple(MediaJobKind.QUARANTINE, 11L),
				org.assertj.core.groups.Tuple.tuple(MediaJobKind.QUARANTINE, 12L));
		assertThat(report.getStatus()).isEqualTo(ReportStatus.ACTIONED);
		assertThat(report.getHandledBy()).isEqualTo(ADMIN);
		assertThat(report.getHandledAt()).isEqualTo(NOW);
	}

	@Test
	void 자동_숨김된_글을_숨기면_상태만_바뀌고_격리_작업을_다시_만들지_않는다() {
		// given
		Post post = givenPost(PostStatus.HIDDEN_PENDING);
		givenOpenReport(ReportTargetType.POST, POST_ID);
		// 격리 작업을 다시 만들면 사진이 있어 저장이 일어나도록 둔다(만들지 않으면 안 쓰이는 스텁).
		lenient().when(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID))
				.thenReturn(List.of(media(11L, 0), media(12L, 1)));

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.HIDE);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
		verifyNoInteractions(jobs);
	}

	@Test
	void 신고가_없는_글도_숨길_수_있다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		givenMedia();
		given(reports.findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, POST_ID, ReportStatus.OPEN))
				.willReturn(List.of());

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.HIDE);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
		assertThat(savedJobs()).hasSize(2);
	}

	@Test
	void 복원하면_PENDING이_되고_사진마다_RELEASE_작업이_생긴다() {
		// given
		Post post = givenPost(PostStatus.HIDDEN);
		givenMedia();
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.RESTORE);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING);
		assertThat(savedJobs()).extracting(MediaJob::getKind, MediaJob::getTargetId).containsExactly(
				org.assertj.core.groups.Tuple.tuple(MediaJobKind.RELEASE, 11L),
				org.assertj.core.groups.Tuple.tuple(MediaJobKind.RELEASE, 12L));
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void 숨기지_않은_글을_복원하면_상태와_작업은_그대로고_신고만_기각된다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);
		lenient().when(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID))
				.thenReturn(List.of(media(11L, 0), media(12L, 1)));

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.RESTORE);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		verifyNoInteractions(jobs);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void 보이는_글을_기각하면_글은_그대로고_신고만_DISMISSED다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.DISMISS);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verifyNoInteractions(jobs);
	}

	@Test
	void 자동_숨김된_글을_기각하면_복원되고_RELEASE_작업이_생긴다() {
		// given
		Post post = givenPost(PostStatus.HIDDEN_PENDING);
		givenMedia();
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.DISMISS);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING);
		assertThat(savedJobs()).extracting(MediaJob::getKind, MediaJob::getTargetId).containsExactly(
				org.assertj.core.groups.Tuple.tuple(MediaJobKind.RELEASE, 11L),
				org.assertj.core.groups.Tuple.tuple(MediaJobKind.RELEASE, 12L));
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		assertThat(report.getHandledBy()).isEqualTo(ADMIN);
	}

	@Test
	void 확정_숨김된_글을_기각하면_신고만_닫힌다() {
		// given
		Post post = givenPost(PostStatus.HIDDEN);
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);
		lenient().when(mediaRepository.findAllByPostIdOrderBySortOrder(POST_ID))
				.thenReturn(List.of(media(11L, 0), media(12L, 1)));

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.DISMISS);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verifyNoInteractions(jobs);
	}

	@Test
	void 지운_글의_신고도_기각할_수_있다() {
		// given
		given(posts.findByIdForUpdate(POST_ID)).willReturn(Optional.empty());
		Report report = givenOpenReport(ReportTargetType.POST, POST_ID);

		// when
		service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.DISMISS);

		// then
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verifyNoInteractions(jobs);
	}

	@Test
	void USER_대상에_HIDE와_RESTORE는_400이다() {
		// when / then
		assertValidationFailed(() -> service.act(ADMIN, ReportTargetType.USER, 9L, Action.HIDE));
		assertValidationFailed(() -> service.act(ADMIN, ReportTargetType.USER, 9L, Action.RESTORE));
		verifyNoInteractions(posts, reports, jobs);
	}

	@Test
	void USER_대상은_기각할_수_있다() {
		// given
		Report report = givenOpenReport(ReportTargetType.USER, 9L);

		// when
		service.act(ADMIN, ReportTargetType.USER, 9L, Action.DISMISS);

		// then
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void 없는_글이나_지운_글이나_초안이나_실패한_글을_숨기면_404다() {
		// given
		given(posts.findByIdForUpdate(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.POST, 99L, Action.HIDE))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));

		// given
		Post deleted = givenPost(PostStatus.VISIBLE);
		deleted.softDelete(NOW);

		// when / then
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.HIDE))
				.isInstanceOf(BusinessException.class);

		// given
		givenPost(PostStatus.DRAFT);

		// when / then
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.RESTORE))
				.isInstanceOf(BusinessException.class);

		// given
		givenPost(PostStatus.FAILED);

		// when / then
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.POST, POST_ID, Action.HIDE))
				.isInstanceOf(BusinessException.class);
		verifyNoInteractions(jobs);
		verify(reports, never()).findAllByTargetTypeAndTargetIdAndStatus(any(), anyLong(), any());
	}

	@Test
	void 신고_목록은_대상별로_묶고_숨긴_글은_격리본_주소를_준다() {
		// given
		Instant last = NOW.minusSeconds(60);
		given(reports.findOpenTargets(PageRequest.of(0, 20))).willReturn(new PageImpl<>(List.of(
				new ReportTargetSummary(ReportTargetType.POST, POST_ID, 3L, last),
				new ReportTargetSummary(ReportTargetType.USER, 9L, 1L, last.minusSeconds(60))),
				PageRequest.of(0, 20), 2));
		Report spam1 = report(1L, 3L, ReportTargetType.POST, POST_ID, ReportReason.SPAM, null);
		Report spam2 = report(2L, 4L, ReportTargetType.POST, POST_ID, ReportReason.SPAM, "광고");
		Report other = report(3L, 5L, ReportTargetType.POST, POST_ID, ReportReason.OTHER, "기타 사유");
		given(reports.findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus.OPEN, ReportTargetType.POST,
				List.of(POST_ID))).willReturn(List.of(spam1, spam2, other));
		given(reports.findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus.OPEN, ReportTargetType.USER,
				List.of(9L))).willReturn(List.of(report(4L, 3L, ReportTargetType.USER, 9L, ReportReason.ABUSE, null)));
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN_PENDING);
		given(posts.findAllById(List.of(POST_ID))).willReturn(List.of(post));
		PostMedia ready = media(11L, 0);
		ready.markReady("2026/11/11.jpg", 800, 600, 1000L);
		PostMedia processing = media(12L, 1);
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(List.of(POST_ID))).willReturn(List.of(ready, processing));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(AUTHOR, "작성자"), user(9L, "대상")));
		given(storage.quarantineExists("p/k11/d.webp")).willReturn(true);
		given(storage.presignQuarantineGet(eq("p/k11/d.webp"), eq(Duration.ofMinutes(5))))
				.willReturn(URI.create("https://in.example/quarantine/p/k11/d.webp?sig"));

		// when
		PageResponse<ReportTargetResponse> page = service.list(0, 20);

		// then
		assertThat(page.totalElements()).isEqualTo(2);
		ReportTargetResponse postTarget = page.items().get(0);
		assertThat(postTarget.targetType()).isEqualTo("POST");
		assertThat(postTarget.targetStatus()).isEqualTo("HIDDEN_PENDING");
		assertThat(postTarget.reportCount()).isEqualTo(3);
		assertThat(postTarget.reasons()).isEqualTo(Map.of("SPAM", 2, "OTHER", 1));
		assertThat(postTarget.details()).containsExactly("기타 사유", "광고");
		assertThat(postTarget.lastReportedAt()).isEqualTo(last.atOffset(ZoneOffset.UTC));
		assertThat(postTarget.post().author().nickname()).isEqualTo("작성자");
		assertThat(postTarget.post().media()).allSatisfy(m -> assertThat(m.url()).isNull());
		assertThat(postTarget.quarantineUrls()).containsExactly("https://in.example/quarantine/p/k11/d.webp?sig");
		assertThat(postTarget.userNickname()).isNull();
		ReportTargetResponse userTarget = page.items().get(1);
		assertThat(userTarget.targetType()).isEqualTo("USER");
		assertThat(userTarget.targetStatus()).isEqualTo("ACTIVE");
		assertThat(userTarget.userNickname()).isEqualTo("대상");
		assertThat(userTarget.post()).isNull();
		assertThat(userTarget.quarantineUrls()).isEmpty();
		assertThat(userTarget.cleanupFailed()).isFalse();
	}

	@Test
	void 보이는_글은_격리본_주소가_없다() {
		// given
		given(reports.findOpenTargets(PageRequest.of(0, 20))).willReturn(new PageImpl<>(
				List.of(new ReportTargetSummary(ReportTargetType.POST, POST_ID, 1L, NOW)), PageRequest.of(0, 20), 1));
		given(reports.findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus.OPEN, ReportTargetType.POST,
				List.of(POST_ID))).willReturn(List.of(report(1L, 3L, ReportTargetType.POST, POST_ID,
						ReportReason.SPAM, null)));
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", PostStatus.VISIBLE);
		given(posts.findAllById(List.of(POST_ID))).willReturn(List.of(post));
		PostMedia ready = media(11L, 0);
		ready.markReady("2026/11/11.jpg", 800, 600, 1000L);
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(List.of(POST_ID))).willReturn(List.of(ready));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(AUTHOR, "작성자")));

		// when
		ReportTargetResponse target = service.list(0, 20).items().get(0);

		// then
		assertThat(target.quarantineUrls()).isEmpty();
		assertThat(target.post().media().get(0).url()).isEqualTo("https://m.example/p/k11/d.webp");
		assertThat(target.cleanupFailed()).isFalse();
		verifyNoInteractions(storage);
	}

	@Test
	void 숨긴_글의_사진은_격리본이_있으면_격리본을_격리_전이면_공개본을_주고_둘_다_없으면_뺀다() {
		// given: 복원 직후 다시 숨겨 PROCESSING인데 격리된 사진, 아직 격리 전인 READY 사진, 아무 데도 없는 사진
		given(reports.findOpenTargets(PageRequest.of(0, 20))).willReturn(new PageImpl<>(
				List.of(new ReportTargetSummary(ReportTargetType.POST, POST_ID, 1L, NOW)), PageRequest.of(0, 20), 1));
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
		given(posts.findAllById(List.of(POST_ID))).willReturn(List.of(post));
		PostMedia requarantined = media(11L, 0);
		requarantined.markProcessing();
		PostMedia notYet = media(12L, 1);
		notYet.markReady("2026/11/12.jpg", 800, 600, 1000L);
		PostMedia gone = media(13L, 2);
		gone.markReady("2026/11/13.jpg", 800, 600, 1000L);
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(List.of(POST_ID)))
				.willReturn(List.of(requarantined, notYet, gone));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(AUTHOR, "작성자")));
		given(storage.quarantineExists("p/k11/d.webp")).willReturn(true);
		given(storage.presignQuarantineGet(eq("p/k11/d.webp"), eq(Duration.ofMinutes(5))))
				.willReturn(URI.create("https://in.example/quarantine/p/k11/d.webp?sig"));
		given(storage.publicExists("p/k12/d.webp")).willReturn(true);

		// when
		ReportTargetResponse target = service.list(0, 20).items().get(0);

		// then
		assertThat(target.quarantineUrls()).containsExactly("https://in.example/quarantine/p/k11/d.webp?sig",
				"https://m.example/p/k12/d.webp");
	}

	@Test
	void 사진_정리가_실패로_끝난_글은_지웠어도_cleanupFailed다() {
		// given
		given(reports.findOpenTargets(PageRequest.of(0, 20))).willReturn(new PageImpl<>(
				List.of(new ReportTargetSummary(ReportTargetType.POST, POST_ID, 1L, NOW)), PageRequest.of(0, 20), 1));
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		post.softDelete(NOW);
		given(posts.findAllById(List.of(POST_ID))).willReturn(List.of(post));
		given(jobs.findPostIdsWithFailedCleanup(List.of(POST_ID))).willReturn(List.of(POST_ID));

		// when
		ReportTargetResponse target = service.list(0, 20).items().get(0);

		// then
		assertThat(target.targetStatus()).isEqualTo("DELETED");
		assertThat(target.cleanupFailed()).isTrue();
	}

	private Comment givenComment(CommentStatus status) {
		Comment comment = Comment.create(POST_ID, AUTHOR, null, null, "댓글 본문");
		ReflectionTestUtils.setField(comment, "id", COMMENT_ID);
		ReflectionTestUtils.setField(comment, "status", status);
		given(comments.findLivePostIdById(COMMENT_ID)).willReturn(Optional.of(POST_ID));
		given(comments.findByIdForUpdate(COMMENT_ID)).willReturn(Optional.of(comment));
		return comment;
	}

	@Test
	void 댓글을_숨기면_HIDDEN이고_수를_다시_센다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		Comment comment = givenComment(CommentStatus.VISIBLE);
		Report report = givenOpenReport(ReportTargetType.COMMENT, COMMENT_ID);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.HIDE);

		// then
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.HIDDEN);
		verify(counter).recount(post);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.ACTIONED);
		verifyNoInteractions(jobs);
	}

	@Test
	void 자동_숨김_댓글을_기각하면_다시_보이고_수가_는다() {
		// given
		Post post = givenPost(PostStatus.VISIBLE);
		Comment comment = givenComment(CommentStatus.HIDDEN_PENDING);
		Report report = givenOpenReport(ReportTargetType.COMMENT, COMMENT_ID);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.DISMISS);

		// then
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.VISIBLE);
		verify(counter).recount(post);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void 확정_숨김_댓글은_기각해도_그대로다() {
		// given
		givenPost(PostStatus.VISIBLE);
		Comment comment = givenComment(CommentStatus.HIDDEN);
		Report report = givenOpenReport(ReportTargetType.COMMENT, COMMENT_ID);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.DISMISS);

		// then
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.HIDDEN);
		verify(counter, never()).recount(any());
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void 지운_글의_댓글은_숨김이_404이고_기각은_된다() {
		// given
		Post deleted = givenPost(PostStatus.VISIBLE);
		deleted.softDelete(NOW);
		given(comments.findLivePostIdById(COMMENT_ID)).willReturn(Optional.of(POST_ID));

		// when / then
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.HIDE))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.RESTORE))
				.isInstanceOf(BusinessException.class);
		verify(comments, never()).findByIdForUpdate(anyLong());

		// given
		Report report = givenOpenReport(ReportTargetType.COMMENT, COMMENT_ID);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.DISMISS);

		// then
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verify(counter, never()).recount(any());
	}

	@Test
	void 지운_댓글은_숨김이_404이고_기각은_된다() {
		// given
		given(comments.findLivePostIdById(COMMENT_ID)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.HIDE))
				.isInstanceOf(BusinessException.class);

		// given
		Report report = givenOpenReport(ReportTargetType.COMMENT, COMMENT_ID);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.DISMISS);

		// then
		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verifyNoInteractions(posts);
	}

	@Test
	void 숨겨진_글의_댓글도_숨기고_복원할_수_있다() {
		// given
		Post post = givenPost(PostStatus.HIDDEN);
		Comment comment = givenComment(CommentStatus.VISIBLE);
		givenOpenReport(ReportTargetType.COMMENT, COMMENT_ID);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.HIDE);

		// then
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.HIDDEN);

		// when
		service.act(ADMIN, ReportTargetType.COMMENT, COMMENT_ID, Action.RESTORE);

		// then
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.VISIBLE);
		verify(counter, times(2)).recount(post);
		assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
	}

	@Test
	void 신고_목록의_댓글_항목은_본문과_원_게시물을_준다() {
		// given
		given(reports.findOpenTargets(PageRequest.of(0, 20))).willReturn(new PageImpl<>(List.of(
				new ReportTargetSummary(ReportTargetType.COMMENT, COMMENT_ID, 1L, NOW),
				new ReportTargetSummary(ReportTargetType.COMMENT, 81L, 1L, NOW.minusSeconds(1))),
				PageRequest.of(0, 20), 2));
		given(reports.findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus.OPEN, ReportTargetType.COMMENT,
				List.of(COMMENT_ID, 81L))).willReturn(List.of(
						report(1L, 3L, ReportTargetType.COMMENT, COMMENT_ID, ReportReason.SPAM, "광고")));
		Comment comment = givenCommentRow(COMMENT_ID, POST_ID, CommentStatus.HIDDEN_PENDING);
		Comment onDeletedPost = givenCommentRow(81L, 51L, CommentStatus.VISIBLE);
		given(comments.findAllById(List.of(COMMENT_ID, 81L))).willReturn(List.of(comment, onDeletedPost));
		Post post = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
		Post gone = Post.draft(AUTHOR);
		ReflectionTestUtils.setField(gone, "id", 51L);
		gone.softDelete(NOW);
		given(posts.findAllById(List.of(POST_ID, 51L))).willReturn(List.of(post, gone));
		PostMedia ready = media(11L, 0);
		ready.markReady("2026/11/11.jpg", 800, 600, 1000L);
		given(mediaRepository.findAllByPostIdInOrderBySortOrder(List.of(POST_ID, 51L))).willReturn(List.of(ready));
		given(users.findAllById(anyCollection())).willReturn(List.of(user(AUTHOR, "작성자")));

		// when
		PageResponse<ReportTargetResponse> page = service.list(0, 20);

		// then
		ReportTargetResponse target = page.items().get(0);
		assertThat(target.targetType()).isEqualTo("COMMENT");
		assertThat(target.targetStatus()).isEqualTo("HIDDEN_PENDING");
		assertThat(target.comment().id()).isEqualTo(COMMENT_ID);
		assertThat(target.comment().postId()).isEqualTo(POST_ID);
		assertThat(target.comment().author().nickname()).isEqualTo("작성자");
		assertThat(target.comment().body()).isEqualTo("댓글 본문");
		assertThat(target.post().id()).isEqualTo(POST_ID);
		assertThat(target.post().media()).allSatisfy(m -> assertThat(m.url()).isNull());
		assertThat(target.quarantineUrls()).isEmpty();
		assertThat(target.details()).containsExactly("광고");
		assertThat(target.cleanupFailed()).isFalse();
		ReportTargetResponse deleted = page.items().get(1);
		assertThat(deleted.targetStatus()).isEqualTo("DELETED");
		assertThat(deleted.comment()).isNull();
		assertThat(deleted.post()).isNull();
		verifyNoInteractions(storage);
	}

	private static Comment givenCommentRow(long id, long postId, CommentStatus status) {
		Comment comment = Comment.create(postId, AUTHOR, null, null, "댓글 본문");
		ReflectionTestUtils.setField(comment, "id", id);
		ReflectionTestUtils.setField(comment, "status", status);
		return comment;
	}

	private static Report report(long id, long reporter, ReportTargetType type, long targetId, ReportReason reason,
			String detail) {
		Report report = Report.open(reporter, type, targetId, reason, detail);
		ReflectionTestUtils.setField(report, "id", id);
		return report;
	}

	private static User user(long id, String nickname) {
		User user = User.create(nickname);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static void assertValidationFailed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
	}
}
