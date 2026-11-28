package com.theclimbingnight.profile.repository;

import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from ProfileImage i where i.id = :id")
	Optional<ProfileImage> findByIdForUpdate(@Param("id") long id);

	long countByUserIdAndStatus(long userId, ProfileImageStatus status);

	List<ProfileImage> findAllByUserIdAndStatus(long userId, ProfileImageStatus status);

	List<ProfileImage> findAllByUserId(long userId);

	/** createdBefore보다 먼저 만들어진 해당 상태 사진 id. */
	@Query("select i.id from ProfileImage i where i.status = :status and i.createdAt < :createdBefore")
	List<Long> findIdsByStatusCreatedBefore(@Param("status") ProfileImageStatus status,
			@Param("createdBefore") Instant createdBefore);

	boolean existsByUserIdAndStatusAndIdGreaterThan(long userId, ProfileImageStatus status, long id);
}
