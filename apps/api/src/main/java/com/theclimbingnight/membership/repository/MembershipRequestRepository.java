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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRequestRepository extends JpaRepository<MembershipRequest, Long> {

	boolean existsByUserIdAndStatus(long userId, MembershipRequestStatus status);

	Optional<MembershipRequest> findFirstByUserIdOrderByIdDesc(long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MembershipRequest m where m.id = :id")
	Optional<MembershipRequest> findByIdForUpdate(@Param("id") long id);

	Page<MembershipRequest> findByStatus(MembershipRequestStatus status, Pageable pageable);

	List<MembershipRequest> findAllByStatusOrderByIdAsc(MembershipRequestStatus status);

	List<MembershipRequest> findAllByUserIdAndStatus(long userId, MembershipRequestStatus status);
}
