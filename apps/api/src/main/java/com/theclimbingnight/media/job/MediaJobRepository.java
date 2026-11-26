package com.theclimbingnight.media.job;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaJobRepository extends JpaRepository<MediaJob, Long> {

	/** 지금 실행할 수 있는 작업 id. 우선순위(작을수록 먼저)·id 순. */
	@Query("""
			select j.id from MediaJob j
			where j.status = com.theclimbingnight.media.job.MediaJobStatus.QUEUED and j.nextRunAt <= :now
			order by j.priority asc, j.id asc
			""")
	List<Long> findClaimable(@Param("now") Instant now, Pageable one);

	/** QUEUED일 때만 RUNNING으로 바꾼다. 가져갔으면 1, 이미 누가 가져갔거나 취소됐으면 0. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update MediaJob j set j.status = com.theclimbingnight.media.job.MediaJobStatus.RUNNING,
			       j.lockedAt = :now, j.updatedAt = :now
			where j.id = :id and j.status = com.theclimbingnight.media.job.MediaJobStatus.QUEUED
			""")
	int claim(@Param("id") long id, @Param("now") Instant now);

	/** lockedBefore보다 먼저 잠긴 RUNNING 작업을 QUEUED로 되돌리고 시도 횟수를 늘린다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update MediaJob j set j.status = com.theclimbingnight.media.job.MediaJobStatus.QUEUED,
			       j.attempts = j.attempts + 1, j.lockedAt = null
			where j.status = com.theclimbingnight.media.job.MediaJobStatus.RUNNING and j.lockedAt < :lockedBefore
			""")
	int requeueStale(@Param("lockedBefore") Instant lockedBefore);

	/** 아직 시작 안 한(QUEUED) 해당 종류 작업만 취소한다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update MediaJob j set j.status = com.theclimbingnight.media.job.MediaJobStatus.CANCELED
			where j.targetId in :mediaIds and j.kind = :kind
			  and j.status = com.theclimbingnight.media.job.MediaJobStatus.QUEUED
			""")
	int cancelQueued(@Param("mediaIds") Collection<Long> mediaIds, @Param("kind") MediaJobKind kind);

	/**
	 * postIds 중 격리·삭제 작업이 FAILED로 끝났고, 그 뒤로 같은 사진에 정리 관련 작업(QUARANTINE·DELETE_MEDIA·RELEASE)이
	 * 새로 생기지 않은 사진이 있는 게시물 id. 즉 사진마다 가장 최근 정리 작업이 실패한 경우만 센다.
	 * 운영진 신고 목록의 cleanupFailed 표시용.
	 */
	@Query("""
			select distinct m.postId from MediaJob j, com.theclimbingnight.feed.entity.PostMedia m
			where j.targetId = m.id and m.postId in :postIds
			  and j.status = com.theclimbingnight.media.job.MediaJobStatus.FAILED
			  and j.kind in (com.theclimbingnight.media.job.MediaJobKind.QUARANTINE,
			                 com.theclimbingnight.media.job.MediaJobKind.DELETE_MEDIA)
			  and not exists (
			      select 1 from MediaJob later
			      where later.targetId = j.targetId and later.id > j.id
			        and later.kind in (com.theclimbingnight.media.job.MediaJobKind.QUARANTINE,
			                           com.theclimbingnight.media.job.MediaJobKind.DELETE_MEDIA,
			                           com.theclimbingnight.media.job.MediaJobKind.RELEASE))
			""")
	List<Long> findPostIdsWithFailedCleanup(@Param("postIds") Collection<Long> postIds);
}
