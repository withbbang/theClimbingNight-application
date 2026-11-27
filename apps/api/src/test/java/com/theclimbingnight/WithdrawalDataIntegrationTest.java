package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.auth.entity.RefreshToken;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.comment.dto.CommentDisplay;
import com.theclimbingnight.comment.dto.CommentResponse;
import com.theclimbingnight.comment.entity.Comment;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.comment.service.CommentQueryService;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostLike;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.repository.InquiryRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobStatus;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushTicket;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.push.repository.PushTicketRepository;
import com.theclimbingnight.push.repository.PushTokenRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.entity.UserStatus;
import com.theclimbingnight.user.repository.UserRepository;
import com.theclimbingnight.user.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 탈퇴하면 개인정보를 지우고 동호회 명부만 남긴다. H2를 다른 통합 테스트와 같이 쓰므로 쓴 행은 전후에 지운다. */
@SpringBootTest
class WithdrawalDataIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

	@Autowired UserService userService;
	@Autowired UserRepository userRepository;
	@Autowired RefreshTokenRepository refreshTokenRepository;
	@Autowired InquiryRepository inquiryRepository;
	@Autowired MembershipRequestRepository membershipRequestRepository;
	@Autowired RosterMemberRepository rosterMemberRepository;
	@Autowired PushTokenRepository pushTokenRepository;
	@Autowired PushTicketRepository pushTicketRepository;
	@Autowired PostRepository postRepository;
	@Autowired PostMediaRepository postMediaRepository;
	@Autowired MediaJobRepository mediaJobRepository;
	@Autowired CommentRepository commentRepository;
	@Autowired PostLikeRepository postLikeRepository;
	@Autowired CommentQueryService commentQueryService;

	private User leaving;
	private User staying;

	@BeforeEach
	void setUp() {
		clean();
		leaving = userRepository.save(User.create("탈퇴데이터A"));
		staying = userRepository.save(User.create("탈퇴데이터B"));
	}

	@AfterEach
	void tearDown() {
		clean();
		userRepository.deleteAllById(java.util.List.of(leaving.getId(), staying.getId()));
	}

	private void clean() {
		commentRepository.deleteAllInBatch();
		postLikeRepository.deleteAllInBatch();
		mediaJobRepository.deleteAllInBatch();
		postMediaRepository.deleteAllInBatch();
		postRepository.deleteAllInBatch();
		pushTicketRepository.deleteAllInBatch();
		pushTokenRepository.deleteAllInBatch();
		inquiryRepository.deleteAllInBatch();
		membershipRequestRepository.deleteAllInBatch();
		rosterMemberRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
	}

	private void fill(User user, String key) {
		refreshTokenRepository.save(RefreshToken.issue(user.getId(), key.repeat(64), NOW));
		Inquiry inquiry = Inquiry.create(user, "제목", "본문");
		inquiry.answer("답변", staying, NOW);
		inquiryRepository.save(inquiry);
		RosterMember roster = RosterMember.create("소모임" + key, null, RosterSource.APP);
		roster.linkTo(user);
		roster = rosterMemberRepository.save(roster);
		MembershipRequest approved = MembershipRequest.create(user, "소모임" + key);
		approved.approve(roster, staying, NOW);
		membershipRequestRepository.save(approved);
		membershipRequestRepository.save(MembershipRequest.create(user, "소모임" + key));
		String token = "ExponentPushToken[" + key + "]";
		pushTokenRepository.save(PushToken.register(user.getId(), token, PushPlatform.IOS, NOW));
		pushTicketRepository.save(PushTicket.of("ticket-" + key, token, NOW));
	}

	@Test
	void 탈퇴하면_내_기록만_지우고_명부는_연결을_끊어_남긴다() {
		// given
		fill(leaving, "a");
		fill(staying, "b");

		// when
		userService.withdraw(leaving.getId());

		// then
		User withdrawn = userRepository.findById(leaving.getId()).orElseThrow();
		assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(withdrawn.getNickname()).isNull();
		assertThat(refreshTokenRepository.findAll()).extracting(RefreshToken::getUserId)
				.containsExactly(staying.getId());
		assertThat(inquiryRepository.findAll()).extracting(i -> i.getUser().getId()).containsExactly(staying.getId());
		assertThat(membershipRequestRepository.findAll()).extracting(m -> m.getUser().getId())
				.containsExactly(staying.getId(), staying.getId());
		assertThat(pushTokenRepository.findAll()).extracting(PushToken::getUserId).containsExactly(staying.getId());
		assertThat(pushTicketRepository.findAll()).extracting(PushTicket::getTicketId).containsExactly("ticket-b");
		assertThat(rosterMemberRepository.findAll()).extracting(RosterMember::getSomoimNickname)
				.containsExactlyInAnyOrder("소모임a", "소모임b");
		assertThat(rosterMemberRepository.findByUserId(leaving.getId())).isEmpty();
		assertThat(rosterMemberRepository.findByUserId(staying.getId())).isPresent();
	}

	@Test
	void 운영진이_탈퇴해도_그_사람이_답한_다른_회원의_문의는_남는다() {
		// given
		fill(leaving, "a");

		// when
		userService.withdraw(staying.getId());

		// then
		assertThat(inquiryRepository.findAll()).hasSize(1);
		assertThat(membershipRequestRepository.findAll()).hasSize(2);
	}

	@Test
	void 탈퇴하면_게시물이_삭제되고_사진_삭제와_원본_정리_작업이_생긴다() {
		// given
		Post mine = postRepository.save(Post.draft(leaving.getId()));
		PostMedia a = postMediaRepository.save(PostMedia.image(mine.getId(), 0, "uploads/a.jpg", "p/a"));
		PostMedia b = postMediaRepository.save(PostMedia.image(mine.getId(), 1, "uploads/b.jpg", "p/b"));
		Post alreadyDeleted = Post.draft(leaving.getId());
		alreadyDeleted.softDelete(NOW);
		alreadyDeleted = postRepository.save(alreadyDeleted);
		Post others = postRepository.save(Post.draft(staying.getId()));
		PostMedia otherMedia = postMediaRepository.save(PostMedia.image(others.getId(), 0, "uploads/c.jpg", "p/c"));

		// when
		userService.withdraw(leaving.getId());

		// then
		assertThat(postRepository.findById(mine.getId()).orElseThrow().getDeletedAt()).isNotNull();
		assertThat(postRepository.findById(alreadyDeleted.getId()).orElseThrow().getDeletedAt()).isEqualTo(NOW);
		assertThat(postRepository.findById(others.getId()).orElseThrow().getDeletedAt()).isNull();
		var jobs = mediaJobRepository.findAll();
		assertThat(jobs).extracting(MediaJob::getStatus).containsOnly(MediaJobStatus.QUEUED);
		assertThat(jobs).extracting(MediaJob::getTargetId).doesNotContain(otherMedia.getId());
		assertThat(jobs).filteredOn(j -> j.getKind() == MediaJobKind.DELETE_MEDIA).extracting(MediaJob::getTargetId)
				.containsExactlyInAnyOrder(a.getId(), b.getId());
		assertThat(jobs).filteredOn(j -> j.getKind() == MediaJobKind.PURGE_ORIGINAL).extracting(MediaJob::getTargetId)
				.containsExactlyInAnyOrder(a.getId(), b.getId());
	}

	private Post visiblePost(long authorId) {
		Post post = Post.draft(authorId);
		post.publish("본문");
		post.markVisible(NOW);
		return postRepository.save(post);
	}

	@Test
	void 탈퇴한_사람의_댓글에_남의_답글이_있으면_삭제_자리로_남는다() {
		// given
		Post post = visiblePost(staying.getId());
		Comment mine = commentRepository.save(Comment.create(post.getId(), leaving.getId(), null, null, "내 댓글"));
		Comment reply = commentRepository
				.save(Comment.create(post.getId(), staying.getId(), mine.getId(), leaving.getId(), "남의 답글"));
		Comment lone = commentRepository.save(Comment.create(post.getId(), leaving.getId(), null, null, "혼자 댓글"));
		postLikeRepository.save(PostLike.of(post.getId(), leaving.getId(), NOW));
		post.updateLikeCount(1);
		post.updateCommentCount(3);
		postRepository.save(post);

		// when
		userService.withdraw(leaving.getId());

		// then
		var items = commentQueryService.list(staying.getId(), Role.USER, post.getId(), null, null, 20).items();
		assertThat(items).extracting(CommentResponse::id).containsExactly(mine.getId());
		assertThat(items.get(0).display()).isEqualTo(CommentDisplay.DELETED);
		assertThat(items.get(0).replyCount()).isEqualTo(1);
		assertThat(items.get(0).body()).isNull();
		assertThat(commentRepository.findById(lone.getId()).orElseThrow().getDeletedAt()).isNotNull();
		assertThat(commentRepository.findById(reply.getId()).orElseThrow().getDeletedAt()).isNull();
		Post after = postRepository.findById(post.getId()).orElseThrow();
		assertThat(after.getLikeCount()).isZero();
		assertThat(after.getCommentCount()).isEqualTo(1);
		assertThat(postLikeRepository.countByPostId(post.getId())).isZero();
	}

	@Test
	void 탈퇴한_사람에게_단_답글은_대상_닉네임이_비어_있다() {
		// given
		Post post = visiblePost(staying.getId());
		Comment parent = commentRepository.save(Comment.create(post.getId(), staying.getId(), null, null, "부모"));
		Comment reply = commentRepository
				.save(Comment.create(post.getId(), staying.getId(), parent.getId(), leaving.getId(), "답글"));
		Comment before = reply;

		// when
		userService.withdraw(leaving.getId());

		// then
		var replies = commentQueryService.list(staying.getId(), Role.USER, post.getId(), parent.getId(), null, 20)
				.items();
		assertThat(replies).extracting(CommentResponse::id).containsExactly(before.getId());
		assertThat(replies.get(0).replyToNickname()).isNull();
		assertThat(replies.get(0).body()).isEqualTo("답글");
	}
}
