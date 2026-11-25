package com.theclimbingnight.inquiry.repository;

import com.theclimbingnight.inquiry.entity.Inquiry;
import com.theclimbingnight.inquiry.entity.InquiryStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	/** 사용자는 읽지 않는다. 사용자 행은 서비스가 먼저 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Inquiry i where i.id = :id")
	Optional<Inquiry> findByIdForUpdate(@Param("id") long id);

	Page<Inquiry> findAllByUserId(long userId, Pageable pageable);

	/** 운영진 화면용: 문의자 닉네임을 함께 읽는다. status가 null이면 전체. */
	@Query(value = "select i from Inquiry i join fetch i.user where (:status is null or i.status = :status)",
			countQuery = "select count(i) from Inquiry i where (:status is null or i.status = :status)")
	Page<Inquiry> findAllForAdmin(@Param("status") InquiryStatus status, Pageable pageable);

	@Query("select i from Inquiry i join fetch i.user where i.id = :id")
	Optional<Inquiry> findWithUserById(@Param("id") long id);

	/** 탈퇴 리스너가 쓴다. 영속성 컨텍스트를 비우지 않는 벌크 삭제. */
	@Modifying
	@Query("delete from Inquiry i where i.user.id = :userId")
	int deleteAllByUserId(@Param("userId") long userId);
}
