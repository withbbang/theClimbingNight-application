package com.theclimbingnight.membership.repository;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRequestRepository extends JpaRepository<MembershipRequest, Long> {

	boolean existsByUserIdAndStatus(long userId, MembershipRequestStatus status);

	Optional<MembershipRequest> findFirstByUserIdOrderByIdDesc(long userId);

	/**
	 * 잠그기 전에 신청자 ID만 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않아야
	 * 뒤이은 findByIdForUpdate가 캐시된 옛 상태 대신 잠근 시점의 행을 돌려준다.
	 */
	@Query("select m.user.id from MembershipRequest m where m.id = :id")
	Optional<Long> findUserIdById(@Param("id") long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MembershipRequest m where m.id = :id")
	Optional<MembershipRequest> findByIdForUpdate(@Param("id") long id);

	Page<MembershipRequest> findByStatus(MembershipRequestStatus status, Pageable pageable);

	List<MembershipRequest> findAllByStatusOrderByIdAsc(MembershipRequestStatus status);

	/** 탈퇴 리스너가 쓴다. 영속성 컨텍스트를 비우지 않는 벌크 삭제. */
	@Modifying
	@Query("delete from MembershipRequest m where m.user.id = :userId")
	int deleteAllByUserId(@Param("userId") long userId);
}
