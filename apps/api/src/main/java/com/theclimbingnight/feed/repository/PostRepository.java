package com.theclimbingnight.feed.repository;

import com.theclimbingnight.feed.entity.Post;
import jakarta.persistence.LockModeType;
import com.theclimbingnight.feed.entity.PostStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

	/**
	 * 보이는 글을 게시 시각·id 내림차순으로 준다. 커서(둘 다 null이면 처음부터) 뒤의 글만, viewerId가 있으면
	 * 내가 차단했거나 나를 차단한 사람의 글은 뺀다.
	 */
	@Query("""
			select p from Post p
			where p.status = com.theclimbingnight.feed.entity.PostStatus.VISIBLE
			  and p.deletedAt is null
			  and (:cursorPublishedAt is null
			       or p.publishedAt < :cursorPublishedAt
			       or (p.publishedAt = :cursorPublishedAt and p.id < :cursorId))
			  and (:viewerId is null or not exists (
			       select 1 from UserBlock b
			       where (b.blockerId = :viewerId and b.blockedId = p.authorId)
			          or (b.blockerId = p.authorId and b.blockedId = :viewerId)))
			order by p.publishedAt desc, p.id desc
			""")
	List<Post> findFeed(@Param("viewerId") Long viewerId, @Param("cursorPublishedAt") Instant cursorPublishedAt,
			@Param("cursorId") Long cursorId, Pageable limit);

	/** 비로그인 노출 판정용. 차단과 무관하게 최신 보이는 글 n개의 id. */
	default List<Long> findLatestVisibleIds(int n) {
		return findLatestVisibleIds(org.springframework.data.domain.PageRequest.of(0, n));
	}

	@Query("""
			select p.id from Post p
			where p.status = com.theclimbingnight.feed.entity.PostStatus.VISIBLE and p.deletedAt is null
			order by p.publishedAt desc, p.id desc
			""")
	List<Long> findLatestVisibleIds(Pageable limit);

	/** 내 글 중 statuses에 해당하고 지워지지 않은 것, 최신순 10개. */
	List<Post> findTop10ByAuthorIdAndStatusInAndDeletedAtIsNullOrderByIdDesc(Long authorId,
			Collection<PostStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :id")
	Optional<Post> findByIdForUpdate(@Param("id") long id);

	/** 만든 지 오래된(createdAt이 before보다 이른) 초안 id. before와 같으면 남긴다. */
	@Query("""
			select p.id from Post p
			where p.status = com.theclimbingnight.feed.entity.PostStatus.DRAFT and p.createdAt < :before
			""")
	List<Long> findDraftIdsCreatedBefore(@Param("before") Instant before);
}
