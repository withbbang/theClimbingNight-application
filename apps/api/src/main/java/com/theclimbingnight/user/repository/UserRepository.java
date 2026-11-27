package com.theclimbingnight.user.repository;

import com.theclimbingnight.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByNicknameIgnoreCase(String nickname);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") long id);

	/**
	 * 사용자 행을 id 오름차순으로 공유 잠금(MySQL {@code FOR SHARE})한다. 좋아요·댓글처럼 users를 외래키로 참조하는 행을
	 * 게시물 행 잠금 뒤에 넣을 때 먼저 부른다. 탈퇴(사용자 → 게시물)와 잠금 순서를 맞춰 교착을 막는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select u from User u where u.id in :ids order by u.id asc")
	List<User> findAllByIdForShare(@Param("ids") Collection<Long> ids);
}
