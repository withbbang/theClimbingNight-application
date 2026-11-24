package com.theclimbingnight.push.repository;

import com.theclimbingnight.push.entity.PushToken;
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

/** 삭제는 모두 벌크 쿼리이고 영속성 컨텍스트를 비우지 않는다. (탈퇴 이벤트 리스너 체인 안에서 불리므로) */
public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

	/** 잠금 순서상 사용자 행을 먼저 잠근 뒤에만 부른다. 다른 트랜잭션의 삭제·이전과 겹치지 않게 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from PushToken t where t.expoPushToken = :token")
	Optional<PushToken> findByExpoPushTokenForUpdate(@Param("token") String token);

	@Query("select t.expoPushToken from PushToken t join User u on u.id = t.userId "
			+ "where u.status = com.theclimbingnight.user.entity.UserStatus.ACTIVE and t.userId in :userIds "
			+ "order by t.id")
	List<String> findTokensByUserIds(@Param("userIds") Collection<Long> userIds);

	@Query("select t.expoPushToken from PushToken t join User u on u.id = t.userId "
			+ "left join NotificationSetting s on s.userId = u.id "
			+ "where u.status = com.theclimbingnight.user.entity.UserStatus.ACTIVE "
			+ "and u.role in (com.theclimbingnight.user.entity.Role.MEMBER, com.theclimbingnight.user.entity.Role.ADMIN) "
			+ "and u.id <> :excludeUserId and (s.userId is null or s.newMeetup = true) "
			+ "order by t.id")
	List<String> findTokensForNewMeetup(@Param("excludeUserId") long excludeUserId);

	@Query("select t.expoPushToken from PushToken t join User u on u.id = t.userId "
			+ "left join NotificationSetting s on s.userId = u.id "
			+ "where u.status = com.theclimbingnight.user.entity.UserStatus.ACTIVE "
			+ "and u.id <> :excludeUserId and (s.userId is null or s.newNotice = true) "
			+ "order by t.id")
	List<String> findTokensForNewNotice(@Param("excludeUserId") long excludeUserId);

	/** 발송 시각 이후에 다시 등록(lastSeenAt 갱신)된 토큰은 지우지 않는다. */
	@Modifying
	@Query("delete from PushToken t where t.expoPushToken = :token and t.lastSeenAt <= :sentAt")
	int deleteIfNotSeenSince(@Param("token") String token, @Param("sentAt") Instant sentAt);

	@Modifying
	@Query("delete from PushToken t where t.userId = :userId")
	int deleteAllByUserId(@Param("userId") long userId);

	@Modifying
	@Query("delete from PushToken t where t.userId = :userId and t.expoPushToken = :token")
	int deleteByUserIdAndToken(@Param("userId") long userId, @Param("token") String token);
}
