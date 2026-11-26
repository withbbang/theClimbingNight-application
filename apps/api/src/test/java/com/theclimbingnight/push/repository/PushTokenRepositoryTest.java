package com.theclimbingnight.push.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.auth.entity.RefreshToken;
import com.theclimbingnight.auth.repository.RefreshTokenRepository;
import com.theclimbingnight.common.ConstraintViolations;
import com.theclimbingnight.push.entity.NotificationSetting;
import com.theclimbingnight.push.entity.PushPlatform;
import com.theclimbingnight.push.entity.PushToken;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
class PushTokenRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

	@Autowired PushTokenRepository pushTokenRepository;
	@Autowired NotificationSettingRepository notificationSettingRepository;
	@Autowired RefreshTokenRepository refreshTokenRepository;
	@Autowired UserRepository userRepository;
	@Autowired EntityManager entityManager;

	private User user(String nickname, Role role) {
		User user = User.create(nickname);
		user.changeRole(role);
		return userRepository.saveAndFlush(user);
	}

	private void token(User user, String token) {
		pushTokenRepository.saveAndFlush(PushToken.register(user.getId(), token, PushPlatform.IOS, NOW));
	}

	private void setting(User user, boolean newMeetup, boolean newNotice) {
		notificationSettingRepository.saveAndFlush(NotificationSetting.create(user.getId(), newMeetup, newNotice));
	}

	@Test
	void 대소문자만_다른_토큰은_다른_토큰이다() {
		// given
		User user = user("a", Role.USER);
		token(user, "ExponentPushToken[AbC]");

		// when
		token(user, "ExponentPushToken[abc]");

		// then
		assertThat(pushTokenRepository.findByExpoPushTokenForUpdate("ExponentPushToken[AbC]")).isPresent();
		assertThat(pushTokenRepository.findByExpoPushTokenForUpdate("ExponentPushToken[abc]")).isPresent();
		assertThat(pushTokenRepository.count()).isEqualTo(2);
	}

	@Test
	void 같은_토큰을_두_번_저장하면_유니크_제약이_위반된다() {
		// given
		User user = user("a", Role.USER);
		token(user, "ExponentPushToken[dup]");

		// when, then
		assertThatThrownBy(() -> token(user, "ExponentPushToken[dup]"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(e -> assertThat(ConstraintViolations.violated(e)).contains("uk_push_tokens_token"));
	}

	@Test
	void 새_벙_대상은_활성_동호회원과_운영진이고_개설자와_끈_사람은_빠진다() {
		// given
		User host = user("host", Role.ADMIN);
		User member = user("member", Role.MEMBER);
		User admin = user("admin", Role.ADMIN);
		User plain = user("plain", Role.USER);
		User muted = user("muted", Role.MEMBER);
		User left = user("left", Role.MEMBER);
		token(host, "ExponentPushToken[host]");
		token(member, "ExponentPushToken[member]");
		token(admin, "ExponentPushToken[admin]");
		token(plain, "ExponentPushToken[plain]");
		token(muted, "ExponentPushToken[muted]");
		token(left, "ExponentPushToken[left]");
		setting(muted, false, true);
		setting(member, true, false);
		left.withdraw(NOW);
		userRepository.saveAndFlush(left);

		// when
		List<String> tokens = pushTokenRepository.findTokensForNewMeetup(host.getId());

		// then
		assertThat(tokens).containsExactlyInAnyOrder("ExponentPushToken[member]", "ExponentPushToken[admin]");
	}

	@Test
	void 새_공지_대상은_일반회원도_포함하고_탈퇴자와_끈_사람은_빠진다() {
		// given
		User author = user("author", Role.ADMIN);
		User plain = user("plain", Role.USER);
		User member = user("member", Role.MEMBER);
		User muted = user("muted", Role.USER);
		User left = user("left", Role.USER);
		token(author, "ExponentPushToken[author]");
		token(plain, "ExponentPushToken[plain]");
		token(member, "ExponentPushToken[member]");
		token(muted, "ExponentPushToken[muted]");
		token(left, "ExponentPushToken[left]");
		setting(muted, true, false);
		setting(member, false, true);
		left.withdraw(NOW);
		userRepository.saveAndFlush(left);

		// when
		List<String> tokens = pushTokenRepository.findTokensForNewNotice(author.getId());

		// then
		assertThat(tokens).containsExactlyInAnyOrder("ExponentPushToken[plain]", "ExponentPushToken[member]");
	}

	@Test
	void 설정_행이_없으면_대상이다() {
		// given
		User host = user("host", Role.ADMIN);
		User member = user("member", Role.MEMBER);
		token(member, "ExponentPushToken[member]");

		// when, then
		assertThat(pushTokenRepository.findTokensForNewMeetup(host.getId())).containsExactly("ExponentPushToken[member]");
		assertThat(pushTokenRepository.findTokensForNewNotice(host.getId())).containsExactly("ExponentPushToken[member]");
	}

	@Test
	void 사용자_목록으로_활성_사용자의_토큰만_찾는다() {
		// given
		User a = user("a", Role.USER);
		User b = user("b", Role.USER);
		User left = user("left", Role.USER);
		token(a, "ExponentPushToken[a]");
		token(b, "ExponentPushToken[b]");
		token(left, "ExponentPushToken[left]");
		left.withdraw(NOW);
		userRepository.saveAndFlush(left);

		// when
		List<String> tokens = pushTokenRepository.findTokensByUserIds(List.of(a.getId(), left.getId()));

		// then
		assertThat(tokens).containsExactly("ExponentPushToken[a]");
	}

	@Test
	void 발송_뒤_다시_등록된_토큰은_지우지_않는다() {
		// given
		User user = user("a", Role.USER);
		Instant sentAt = NOW.plusSeconds(60);
		pushTokenRepository.saveAndFlush(PushToken.register(user.getId(), "ExponentPushToken[old]", PushPlatform.IOS, sentAt));
		pushTokenRepository.saveAndFlush(
				PushToken.register(user.getId(), "ExponentPushToken[fresh]", PushPlatform.IOS, sentAt.plusSeconds(1)));
		pushTokenRepository.saveAndFlush(
				PushToken.register(user.getId(), "ExponentPushToken[older]", PushPlatform.IOS, sentAt.minusSeconds(1)));

		// when
		int deletedEqual = pushTokenRepository.deleteIfNotSeenSince("ExponentPushToken[old]", sentAt);
		int deletedFresh = pushTokenRepository.deleteIfNotSeenSince("ExponentPushToken[fresh]", sentAt);
		int deletedOlder = pushTokenRepository.deleteIfNotSeenSince("ExponentPushToken[older]", sentAt);
		entityManager.clear();

		// then
		assertThat(deletedEqual).isEqualTo(1);
		assertThat(deletedFresh).isEqualTo(0);
		assertThat(deletedOlder).isEqualTo(1);
		assertThat(pushTokenRepository.findAll()).extracting(PushToken::getExpoPushToken)
				.containsExactly("ExponentPushToken[fresh]");
	}

	@Test
	void 사용자가_가진_토큰을_값_순서로_찾는다() {
		// given
		User a = user("a", Role.USER);
		User b = user("b", Role.USER);
		token(a, "ExponentPushToken[a2]");
		token(a, "ExponentPushToken[a1]");
		token(b, "ExponentPushToken[b1]");

		// when, then
		assertThat(pushTokenRepository.findTokensOwnedBy(a.getId()))
				.containsExactly("ExponentPushToken[a1]", "ExponentPushToken[a2]");
	}

	private void session(User user, String hashSeed, Instant expiresAt, Instant revokedAt) {
		RefreshToken refresh = RefreshToken.issue(user.getId(), hashSeed.repeat(64), expiresAt);
		if (revokedAt != null) {
			refresh.revoke(revokedAt);
		}
		refreshTokenRepository.saveAndFlush(refresh);
	}

	@Test
	void 살아_있는_세션이_없는_사용자의_토큰은_정리_대상이다() {
		// given: 만료 정각은 만료로 본다
		User alive = user("alive", Role.USER);
		User expired = user("expired", Role.USER);
		User revoked = user("revoked", Role.USER);
		User none = user("none", Role.USER);
		session(alive, "a", NOW.plusSeconds(1), null);
		session(alive, "b", NOW.minusSeconds(1), null);
		session(expired, "c", NOW, null);
		session(revoked, "d", NOW.plusSeconds(100), NOW.minusSeconds(1));
		token(alive, "ExponentPushToken[alive]");
		token(expired, "ExponentPushToken[expired]");
		token(revoked, "ExponentPushToken[revoked]");
		token(none, "ExponentPushToken[none]");

		// when, then
		assertThat(pushTokenRepository.findStaleTokens(NOW, NOW.minusSeconds(60)))
				.extracting(StalePushToken::token).containsExactly(
				"ExponentPushToken[expired]", "ExponentPushToken[revoked]", "ExponentPushToken[none]");
	}

	@Test
	void 세션이_살아_있어도_오래_등록되지_않은_토큰은_정리_대상이다() {
		// given
		User alive = user("alive", Role.USER);
		session(alive, "a", NOW.plusSeconds(1), null);
		Instant staleBefore = NOW.minusSeconds(60);
		pushTokenRepository.saveAndFlush(PushToken.register(alive.getId(), "ExponentPushToken[old]",
				PushPlatform.IOS, staleBefore.minusSeconds(1)));
		pushTokenRepository.saveAndFlush(PushToken.register(alive.getId(), "ExponentPushToken[edge]",
				PushPlatform.IOS, staleBefore));

		// when, then
		assertThat(pushTokenRepository.findStaleTokens(NOW, staleBefore))
				.containsExactly(new StalePushToken("ExponentPushToken[old]", staleBefore.minusSeconds(1)));
	}

	@Test
	void 소유자가_맞을_때만_토큰을_지운다() {
		// given
		User a = user("a", Role.USER);
		User b = user("b", Role.USER);
		token(a, "ExponentPushToken[a1]");

		// when
		int byOther = pushTokenRepository.deleteByUserIdAndToken(b.getId(), "ExponentPushToken[a1]");
		int byOwner = pushTokenRepository.deleteByUserIdAndToken(a.getId(), "ExponentPushToken[a1]");

		// then
		assertThat(byOther).isEqualTo(0);
		assertThat(byOwner).isEqualTo(1);
	}

	@Test
	void 폐기_시각_이전에_본_내_토큰만_지운다() {
		// given: 토큰 3개의 lastSeenAt이 폐기 시각보다 이전, 같음, 이후
		User a = user("a", Role.USER);
		User b = user("b", Role.USER);
		Instant revokedAt = NOW.plusSeconds(100);
		pushTokenRepository.saveAndFlush(PushToken.register(a.getId(), "ExponentPushToken[old]", PushPlatform.IOS, revokedAt.minusSeconds(1)));
		pushTokenRepository.saveAndFlush(PushToken.register(a.getId(), "ExponentPushToken[equal]", PushPlatform.IOS, revokedAt));
		pushTokenRepository.saveAndFlush(PushToken.register(a.getId(), "ExponentPushToken[new]", PushPlatform.IOS, revokedAt.plusSeconds(1)));
		pushTokenRepository.saveAndFlush(PushToken.register(b.getId(), "ExponentPushToken[other]", PushPlatform.IOS, revokedAt.minusSeconds(1)));

		// when
		int old = pushTokenRepository.deleteByUserIdAndTokenIfNotSeenSince(a.getId(), "ExponentPushToken[old]", revokedAt);
		int equal = pushTokenRepository.deleteByUserIdAndTokenIfNotSeenSince(a.getId(), "ExponentPushToken[equal]", revokedAt);
		int reRegistered = pushTokenRepository.deleteByUserIdAndTokenIfNotSeenSince(a.getId(), "ExponentPushToken[new]", revokedAt);
		int othersToken = pushTokenRepository.deleteByUserIdAndTokenIfNotSeenSince(a.getId(), "ExponentPushToken[other]", revokedAt);

		// then
		assertThat(old).isEqualTo(1);
		assertThat(equal).isEqualTo(1);
		assertThat(reRegistered).isEqualTo(0);
		assertThat(othersToken).isEqualTo(0);
		assertThat(pushTokenRepository.findAll()).extracting(PushToken::getExpoPushToken)
				.containsExactlyInAnyOrder("ExponentPushToken[new]", "ExponentPushToken[other]");
	}

	@Test
	void 잠금_조회는_있는_토큰을_찾고_없으면_비어_있다() {
		// given
		User user = user("a", Role.USER);
		token(user, "ExponentPushToken[here]");

		// when, then
		assertThat(pushTokenRepository.findByExpoPushTokenForUpdate("ExponentPushToken[here]")).isPresent();
		assertThat(pushTokenRepository.findByExpoPushTokenForUpdate("ExponentPushToken[gone]")).isEmpty();
	}

	@Test
	void 유니크_위반이_나도_토큰_원문이_로그에_남지_않는다(CapturedOutput output) {
		// given
		User user = user("a", Role.USER);
		token(user, "ExponentPushToken[secretdup]");

		// when
		assertThatThrownBy(() -> token(user, "ExponentPushToken[secretdup]"))
				.isInstanceOf(DataIntegrityViolationException.class);

		// then
		assertThat(output.getAll()).doesNotContain("secretdup");
	}

	@Test
	void 운영진_토큰은_활성_ADMIN만_신고자를_빼고_찾는다() {
		// given
		User reporter = user("reporter", Role.ADMIN);
		User admin = user("admin", Role.ADMIN);
		User left = user("left", Role.ADMIN);
		User member = user("member", Role.MEMBER);
		token(reporter, "ExponentPushToken[reporter]");
		token(admin, "ExponentPushToken[admin]");
		token(left, "ExponentPushToken[left]");
		token(member, "ExponentPushToken[member]");
		left.withdraw(NOW);
		userRepository.saveAndFlush(left);

		// when
		List<String> tokens = pushTokenRepository.findTokensOfAdmins(reporter.getId());

		// then
		assertThat(tokens).containsExactly("ExponentPushToken[admin]");
	}
}
