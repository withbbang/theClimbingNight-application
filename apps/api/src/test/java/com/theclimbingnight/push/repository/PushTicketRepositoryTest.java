package com.theclimbingnight.push.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.push.entity.PushTicket;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PushTicketRepositoryTest {

	private static final Instant CUTOFF = Instant.parse("2026-11-24T12:00:00Z");

	@Autowired PushTicketRepository repository;

	private PushTicket save(String ticketId, Instant sentAt) {
		return repository.saveAndFlush(PushTicket.of(ticketId, "ExponentPushToken[" + ticketId + "]", sentAt));
	}

	@Test
	void 정각에_보낸_티켓은_포함하고_일_마이크로초_뒤는_뺀다() {
		// given
		PushTicket exact = save("exact", CUTOFF);
		PushTicket later = save("later", CUTOFF.plusNanos(1000));
		PushTicket earlier = save("earlier", CUTOFF.minusSeconds(1));

		// when
		List<PushTicket> due = repository.findDue(CUTOFF, 0L, PageRequest.of(0, 100));

		// then
		assertThat(due).extracting(PushTicket::getId).contains(exact.getId(), earlier.getId())
				.doesNotContain(later.getId());
	}

	@Test
	void lastId_뒤의_티켓만_id_순으로_돌려준다() {
		// given
		PushTicket a = save("a", CUTOFF.minusSeconds(60));
		PushTicket b = save("b", CUTOFF.minusSeconds(120));
		PushTicket c = save("c", CUTOFF.minusSeconds(30));

		// when
		List<PushTicket> all = repository.findDue(CUTOFF, a.getId() - 1, PageRequest.of(0, 100));
		List<PushTicket> after = repository.findDue(CUTOFF, a.getId(), PageRequest.of(0, 100));

		// then
		assertThat(all).extracting(PushTicket::getId).containsExactly(a.getId(), b.getId(), c.getId());
		assertThat(after).extracting(PushTicket::getId).containsExactly(b.getId(), c.getId());
	}

	@Test
	void 쪽_크기만큼만_돌려준다() {
		// given
		PushTicket a = save("a", CUTOFF.minusSeconds(60));
		save("b", CUTOFF.minusSeconds(60));
		save("c", CUTOFF.minusSeconds(60));

		// when
		List<PushTicket> page = repository.findDue(CUTOFF, a.getId() - 1, PageRequest.of(0, 2));

		// then
		assertThat(page).hasSize(2);
		assertThat(page.get(0).getId()).isEqualTo(a.getId());
	}

	@Test
	void 발송_시각_순서와_달라도_id_순으로_돌려준다() {
		// given: id는 커지는데 발송 시각은 빨라지는 티켓 40개
		List<Long> expected = new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++) {
			expected.add(save("o" + i, CUTOFF.minusSeconds(100 + i * 10L)).getId());
		}

		// when
		List<PushTicket> due = repository.findDue(CUTOFF, expected.get(0) - 1, PageRequest.of(0, 100));

		// then
		assertThat(due).extracting(PushTicket::getId).containsExactlyElementsOf(expected);
	}
}
