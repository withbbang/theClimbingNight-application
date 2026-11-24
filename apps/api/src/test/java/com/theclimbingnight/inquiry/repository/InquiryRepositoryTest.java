package com.theclimbingnight.inquiry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InquiryRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Sort ID_DESC = Sort.by(Sort.Order.desc("id"));

	@Autowired
	InquiryRepository inquiryRepository;

	@Autowired
	UserRepository userRepository;

	@Test
	void 내_문의만_최신순으로_나온다() {
		// given
		User me = userRepository.save(User.create("me"));
		User other = userRepository.save(User.create("other"));
		Inquiry first = inquiryRepository.save(Inquiry.create(me, "첫", "b"));
		inquiryRepository.save(Inquiry.create(other, "남의", "b"));
		Inquiry second = inquiryRepository.save(Inquiry.create(me, "둘째", "b"));
		inquiryRepository.flush();

		// when
		Page<Inquiry> result = inquiryRepository.findAllByUserId(me.getId(), PageRequest.of(0, 10, ID_DESC));

		// then
		assertThat(result.getContent()).containsExactly(second, first);
		assertThat(result.getTotalElements()).isEqualTo(2);
	}

	@Test
	void 상태로_거르고_없으면_전체다() {
		// given
		User me = userRepository.save(User.create("me"));
		User admin = userRepository.save(User.create("admin"));
		Inquiry answered = inquiryRepository.save(Inquiry.create(me, "답변됨", "b"));
		answered.answer("답", admin, NOW);
		Inquiry open = inquiryRepository.save(Inquiry.create(me, "열림", "b"));
		inquiryRepository.flush();

		// when
		Page<Inquiry> onlyOpen = inquiryRepository.findAllForAdmin(InquiryStatus.OPEN, PageRequest.of(0, 10, ID_DESC));
		Page<Inquiry> all = inquiryRepository.findAllForAdmin(null, PageRequest.of(0, 10, ID_DESC));

		// then
		assertThat(onlyOpen.getContent()).containsExactly(open);
		assertThat(onlyOpen.getTotalElements()).isEqualTo(1);
		assertThat(all.getContent()).containsExactly(open, answered);
		assertThat(all.getTotalElements()).isEqualTo(2);
	}

	@Test
	void 답변하면_상태와_답변자가_저장된다() {
		// given
		User me = userRepository.save(User.create("me"));
		User admin = userRepository.save(User.create("admin"));
		Inquiry saved = inquiryRepository.save(Inquiry.create(me, "t", "b"));
		saved.answer("답", admin, NOW);
		inquiryRepository.flush();

		// when
		Inquiry found = inquiryRepository.findByIdForUpdate(saved.getId()).orElseThrow();

		// then
		assertThat(found.isAnswered()).isTrue();
		assertThat(found.getAnswerer()).isEqualTo(admin);
		assertThat(found.getAnsweredAt()).isEqualTo(NOW);
	}
}
