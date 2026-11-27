package com.theclimbingnight.comment.repository;

import com.theclimbingnight.comment.entity.Comment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Comment c where c.id = :id")
	Optional<Comment> findByIdForUpdate(@Param("id") long id);

	/** 지워지지 않은 댓글의 post_id. 엔티티를 영속성 컨텍스트에 올리지 않고 잠금 전에 게시물을 찾는 데 쓴다. */
	@Query("select c.postId from Comment c where c.id = :id and c.deletedAt is null")
	Optional<Long> findLivePostIdById(@Param("id") long id);

	/** 지워지지 않았고 VISIBLE인 댓글과 답글을 모두 센다. */
	@Query("select count(c) from Comment c where c.postId = :postId and c.deletedAt is null and c.status = com.theclimbingnight.comment.entity.CommentStatus.VISIBLE")
	int countShownByPostId(@Param("postId") long postId);
}
