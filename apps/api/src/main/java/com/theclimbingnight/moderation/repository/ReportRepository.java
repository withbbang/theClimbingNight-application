package com.theclimbingnight.moderation.repository;

import com.theclimbingnight.moderation.entity.Report;
import com.theclimbingnight.moderation.entity.ReportStatus;
import com.theclimbingnight.moderation.entity.ReportTargetType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<Report, Long> {

	long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, long targetId, ReportStatus status);

	List<Report> findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, long targetId,
			ReportStatus status);

	List<Report> findAllByStatusAndTargetTypeAndTargetIdIn(ReportStatus status, ReportTargetType targetType,
			Collection<Long> targetIds);

	/** 열린 신고가 있는 대상을 마지막 신고가 최근인 순으로 준다. 개수는 대상마다 가장 최근 열린 신고 한 줄로 센다. */
	@Query(value = """
			select new com.theclimbingnight.moderation.repository.ReportTargetSummary(
			       r.targetType, r.targetId, count(r), max(r.createdAt))
			from Report r
			where r.status = com.theclimbingnight.moderation.entity.ReportStatus.OPEN
			group by r.targetType, r.targetId
			order by max(r.createdAt) desc, max(r.id) desc
			""", countQuery = """
			select count(r) from Report r
			where r.status = com.theclimbingnight.moderation.entity.ReportStatus.OPEN
			  and r.id = (select max(r2.id) from Report r2
			              where r2.status = com.theclimbingnight.moderation.entity.ReportStatus.OPEN
			                and r2.targetType = r.targetType and r2.targetId = r.targetId)
			""")
	Page<ReportTargetSummary> findOpenTargets(Pageable pageable);
}
