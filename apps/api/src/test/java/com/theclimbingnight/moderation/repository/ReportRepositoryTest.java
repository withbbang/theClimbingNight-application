package com.theclimbingnight.moderation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportReason;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-11-26T00:00:00Z");

	@Autowired ReportRepository reports;
	@Autowired UserRepository users;
	@Autowired EntityManager entityManager;

	private long user(String nickname) {
		return users.saveAndFlush(User.create(nickname)).getId();
	}

	/** 감사(auditing)가 채운 생성 시각을 T0 + minute분으로 덮는다. 네이티브 바인딩의 시간대 해석은 비교에 쓰지 않는다. */
	private Report report(long reporter, ReportTargetType type, long targetId, int minute) {
		Report report = reports.saveAndFlush(Report.open(reporter, type, targetId, ReportReason.SPAM, null));
		entityManager.createNativeQuery("update reports set created_at = ? where id = ?")
				.setParameter(1, T0.plusSeconds(60L * minute)).setParameter(2, report.getId()).executeUpdate();
		return report;
	}

	private Instant createdAt(Report report) {
		return reports.findById(report.getId()).orElseThrow().getCreatedAt();
	}

	@Test
	void 열린_신고가_있는_대상만_최근_신고순으로_묶는다() {
		// given
		long a = user("a");
		long b = user("b");
		long c = user("c");
		report(a, ReportTargetType.POST, 1, 1);
		Report latestPost = report(b, ReportTargetType.POST, 1, 5);
		Report latestUser = report(a, ReportTargetType.USER, 1, 3);
		Report dismissed = report(a, ReportTargetType.POST, 2, 9);
		dismissed.handle(ReportStatus.DISMISSED, c, T0);
		report(c, ReportTargetType.POST, 1, 2);
		reports.flush();
		entityManager.clear();

		// when
		Page<ReportTargetSummary> page = reports.findOpenTargets(PageRequest.of(0, 10));

		// then
		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).extracting(ReportTargetSummary::targetType, ReportTargetSummary::targetId,
				ReportTargetSummary::reportCount, ReportTargetSummary::lastReportedAt).containsExactly(
						tuple(ReportTargetType.POST, 1L, 3L, createdAt(latestPost)),
						tuple(ReportTargetType.USER, 1L, 1L, createdAt(latestUser)));
	}

	@Test
	void 페이지를_나누면_전체_대상_수가_맞다() {
		// given
		long a = user("a");
		report(a, ReportTargetType.POST, 1, 1);
		report(a, ReportTargetType.POST, 2, 2);
		report(user("b"), ReportTargetType.POST, 2, 3);
		report(a, ReportTargetType.POST, 3, 4);
		entityManager.clear();

		// when
		Page<ReportTargetSummary> page = reports.findOpenTargets(PageRequest.of(0, 2));

		// then
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getContent()).extracting(ReportTargetSummary::targetId).containsExactly(3L, 2L);
	}

	@Test
	void 대상의_열린_신고만_센다() {
		// given
		long a = user("a");
		long b = user("b");
		report(a, ReportTargetType.POST, 1, 1);
		Report handled = report(b, ReportTargetType.POST, 1, 2);
		handled.handle(ReportStatus.ACTIONED, a, T0);
		reports.flush();

		// when / then
		assertThat(reports.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, 1L, ReportStatus.OPEN))
				.isEqualTo(1);
		assertThat(reports.findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus.OPEN, ReportTargetType.POST,
				List.of(1L, 2L))).hasSize(1);
	}
}
