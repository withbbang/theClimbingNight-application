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
}
