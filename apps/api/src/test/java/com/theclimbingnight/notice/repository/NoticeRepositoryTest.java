package com.theclimbingnight.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.notice.entity.Notice;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NoticeRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	NoticeRepository noticeRepository;

	@Autowired
	UserRepository userRepository;

	@Test
	void 고정_공지가_먼저고_그다음은_최신순이다() {
		// given
		User admin = userRepository.save(User.create("admin"));
		Notice oldPinned = noticeRepository.save(Notice.create("고정 옛것", "b", true, admin, NOW.minusSeconds(100)));
		Notice oldNormal = noticeRepository.save(Notice.create("일반 옛것", "b", false, admin, NOW.minusSeconds(50)));
		Notice newPinned = noticeRepository.save(Notice.create("고정 새것", "b", true, admin, NOW));
		Notice sameTimeFirst = noticeRepository.save(Notice.create("일반 같은시각 먼저", "b", false, admin, NOW));
		Notice sameTimeLast = noticeRepository.save(Notice.create("일반 같은시각 나중", "b", false, admin, NOW));
		noticeRepository.flush();

		// when
		List<Notice> result = noticeRepository.findAll(PageRequest.of(0, 10,
				Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"), Sort.Order.desc("id")))).getContent();

		// then
		assertThat(result).containsExactly(newPinned, oldPinned, sameTimeLast, sameTimeFirst, oldNormal);
	}
}
