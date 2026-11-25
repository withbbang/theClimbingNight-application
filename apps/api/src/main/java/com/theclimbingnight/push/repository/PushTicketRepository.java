package com.theclimbingnight.push.repository;

import com.theclimbingnight.push.entity.PushTicket;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTicketRepository extends JpaRepository<PushTicket, Long> {

	/** 잠그지 않고 읽는다. 수신 확인 작업과 같이 기본 키로 지워야 잠금 순서가 엇갈리지 않는다. */
	@Query("select t.id from PushTicket t where t.expoPushToken in :tokens order by t.id")
	List<Long> findIdsByTokens(@Param("tokens") Collection<String> tokens);

	/** id 순으로 lastId 뒤의 확인 대상 티켓. 남겨 둔 티켓이 다음 쪽에 다시 나오지 않게 id로 이어서 읽는다. */
	@Query("select t from PushTicket t where t.sentAt <= :cutoff and t.id > :lastId order by t.id")
	List<PushTicket> findDue(@Param("cutoff") Instant cutoff, @Param("lastId") long lastId, Pageable pageable);
}
