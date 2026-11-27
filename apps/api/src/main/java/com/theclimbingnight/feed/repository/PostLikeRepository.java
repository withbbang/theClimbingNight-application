package com.theclimbingnight.feed.repository;

import com.theclimbingnight.feed.entity.PostLike;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLike.Key> {

	boolean existsByPostIdAndUserId(long postId, long userId);

	/** 좋아요를 지운다. 지운 행 수(0 또는 1)를 준다. */
	@Modifying
	@Query("delete from PostLike l where l.postId = :postId and l.userId = :userId")
	int deleteLike(@Param("postId") long postId, @Param("userId") long userId);

	long countByPostId(long postId);

	/** postIds 중 userId가 좋아요한 게시물 id. */
	@Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
	List<Long> findLikedPostIds(@Param("userId") long userId, @Param("postIds") Collection<Long> postIds);

	/** 탈퇴 정리용. userId가 좋아요한 모든 게시물 id. */
	@Query("select l.postId from PostLike l where l.userId = :userId")
	List<Long> findPostIdsByUserId(@Param("userId") long userId);

	/**
	 * 좋아요 누른 사람을 (created_at, user_id) 내림차순으로 준다. 커서(둘 다 null이면 처음부터) 뒤만, 내가 차단했거나
	 * 나를 차단한 사람은 뺀다.
	 */
	@Query("""
			select l from PostLike l
			where l.postId = :postId
			  and (:cursorCreatedAt is null
			       or l.createdAt < :cursorCreatedAt
			       or (l.createdAt = :cursorCreatedAt and l.userId < :cursorUserId))
			  and not exists (
			       select 1 from UserBlock b
			       where (b.blockerId = :viewerId and b.blockedId = l.userId)
			          or (b.blockerId = l.userId and b.blockedId = :viewerId))
			order by l.createdAt desc, l.userId desc
			""")
	List<PostLike> findLikers(@Param("postId") long postId, @Param("viewerId") long viewerId,
			@Param("cursorCreatedAt") Instant cursorCreatedAt, @Param("cursorUserId") Long cursorUserId,
			Pageable limit);
}
