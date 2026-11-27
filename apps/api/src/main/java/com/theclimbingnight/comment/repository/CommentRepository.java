package com.theclimbingnight.comment.repository;

import com.theclimbingnight.comment.entity.Comment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * 최상위 댓글을 오래된 순으로 준다. 차단 관계인 작성자는 뺀다. 보이는 댓글, 작성자 본인의 숨긴 댓글,
	 * 보이는 답글이 남은 지움·숨김 자리가 포함된다.
	 */
	@Query("""
			select c from Comment c
			where c.postId = :postId and c.parentId is null
			  and (:cursorId is null or c.id > :cursorId)
			  and not exists (
			       select 1 from UserBlock b
			       where (b.blockerId = :viewerId and b.blockedId = c.authorId)
			          or (b.blockerId = c.authorId and b.blockedId = :viewerId))
			  and ((c.deletedAt is null and c.status = com.theclimbingnight.comment.entity.CommentStatus.VISIBLE)
			       or (c.authorId = :viewerId and c.deletedAt is null
			           and c.status in (com.theclimbingnight.comment.entity.CommentStatus.HIDDEN_PENDING,
			                            com.theclimbingnight.comment.entity.CommentStatus.HIDDEN))
			       or exists (select 1 from Comment r
			                  where r.parentId = c.id and r.deletedAt is null
			                    and r.status = com.theclimbingnight.comment.entity.CommentStatus.VISIBLE))
			order by c.id asc
			""")
	List<Comment> findTopLevel(@Param("postId") long postId, @Param("viewerId") long viewerId,
			@Param("cursorId") Long cursorId, Pageable limit);

	/** 한 부모의 답글을 오래된 순으로 준다. 차단 관계인 작성자의 답글은 빠지고, 숨긴 답글은 작성자에게만 나온다. */
	@Query("""
			select c from Comment c
			where c.parentId = :parentId
			  and (:cursorId is null or c.id > :cursorId)
			  and ((c.deletedAt is null and c.status = com.theclimbingnight.comment.entity.CommentStatus.VISIBLE
			        and not exists (
			             select 1 from UserBlock b
			             where (b.blockerId = :viewerId and b.blockedId = c.authorId)
			                or (b.blockerId = c.authorId and b.blockedId = :viewerId)))
			       or (c.authorId = :viewerId and c.deletedAt is null
			           and c.status in (com.theclimbingnight.comment.entity.CommentStatus.HIDDEN_PENDING,
			                            com.theclimbingnight.comment.entity.CommentStatus.HIDDEN)))
			order by c.id asc
			""")
	List<Comment> findReplies(@Param("parentId") long parentId, @Param("viewerId") long viewerId,
			@Param("cursorId") Long cursorId, Pageable limit);

	/** 부모별 보이는 답글 수. 보이는 답글이 없는 부모는 결과에 없다. */
	@Query("""
			select new com.theclimbingnight.comment.repository.ReplyCount(c.parentId, count(c)) from Comment c
			where c.parentId in :parentIds and c.deletedAt is null
			  and c.status = com.theclimbingnight.comment.entity.CommentStatus.VISIBLE
			group by c.parentId
			""")
	List<ReplyCount> countShownReplies(@Param("parentIds") Collection<Long> parentIds);

	/** 탈퇴 정리용. userId가 지워지지 않은 댓글을 남긴 게시물 id. 중복 없음. */
	@Query("select distinct c.postId from Comment c where c.authorId = :authorId and c.deletedAt is null")
	List<Long> findLivePostIdsByAuthorId(@Param("authorId") long authorId);

	/** 한 게시물에서 작성자의 지워지지 않은 댓글·답글을 소프트 삭제한다. 지운 행 수를 준다. 호출하는 쪽이 게시물 행을 잠가야 한다. */
	@Modifying
	@Query("update Comment c set c.deletedAt = :now where c.postId = :postId and c.authorId = :authorId and c.deletedAt is null")
	int softDeleteByPostIdAndAuthorId(@Param("postId") long postId, @Param("authorId") long authorId,
			@Param("now") Instant now);
}
