package com.theclimbingnight.profile.repository;

import com.theclimbingnight.profile.entity.ProfileImage;
import com.theclimbingnight.profile.entity.ProfileImageStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from ProfileImage i where i.id = :id")
	Optional<ProfileImage> findByIdForUpdate(@Param("id") long id);

	/** 엔티티를 올리지 않고 주인 id만 읽는다. 사용자 행을 먼저 잠근 뒤 사진 행을 잠가 읽을 때 쓴다. */
	@Query("select i.userId from ProfileImage i where i.id = :id")
	Optional<Long> findUserIdById(@Param("id") long id);

	long countByUserIdAndStatus(long userId, ProfileImageStatus status);

	List<ProfileImage> findAllByUserIdAndStatus(long userId, ProfileImageStatus status);

	/** 사용자의 UPLOADING 행을 한 번에 지운다. 정리 작업이 먼저 지운 행이어도 오류 없이 0건으로 끝난다. */
	@Modifying(flushAutomatically = true)
	@Query("delete from ProfileImage i where i.userId = :userId and i.status = :status")
	int deleteByUserIdAndStatus(@Param("userId") long userId, @Param("status") ProfileImageStatus status);

	List<ProfileImage> findAllByUserId(long userId);

	/** createdBefore보다 먼저 만들어진 해당 상태 사진 id. */
	@Query("select i.id from ProfileImage i where i.status = :status and i.createdAt < :createdBefore")
	List<Long> findIdsByStatusCreatedBefore(@Param("status") ProfileImageStatus status,
			@Param("createdBefore") Instant createdBefore);

	boolean existsByUserIdAndStatusAndIdGreaterThan(long userId, ProfileImageStatus status, long id);

	record CurrentImage(Long userId, String publicKey) {
	}

	/** 사용자의 지금 프로필 사진 중 READY인 것만. */
	@Query("""
			select new com.theclimbingnight.profile.repository.ProfileImageRepository$CurrentImage(u.id, i.publicKey)
			from User u join ProfileImage i on i.id = u.profileImageId
			where u.id in :userIds and i.status = com.theclimbingnight.profile.entity.ProfileImageStatus.READY
			""")
	List<CurrentImage> findCurrentReady(@Param("userIds") Collection<Long> userIds);
}
