package com.theclimbingnight.roster.repository;

import com.theclimbingnight.roster.entity.RosterMember;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RosterMemberRepository extends JpaRepository<RosterMember, Long> {

	Optional<RosterMember> findByUserId(long userId);

	List<RosterMember> findAllByUserIdIn(Collection<Long> userIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RosterMember r where r.id = :id")
	Optional<RosterMember> findByIdForUpdate(@Param("id") long id);

	@Query("select r from RosterMember r where lower(r.somoimNickname) in :lowered and r.user is null")
	List<RosterMember> findUnlinkedByLowerNicknames(@Param("lowered") Collection<String> loweredNicknames);

	@Query("select distinct lower(r.somoimNickname) from RosterMember r where lower(r.somoimNickname) in :lowered")
	List<String> findExistingLowerNicknames(@Param("lowered") Collection<String> loweredNicknames);

	@Query(value = "select r from RosterMember r where "
			+ "(:q is null or lower(r.somoimNickname) like lower(concat('%', :q, '%'))) "
			+ "and (:linked is null or (:linked = true and r.user is not null) "
			+ "or (:linked = false and r.user is null))",
			countQuery = "select count(r) from RosterMember r where "
			+ "(:q is null or lower(r.somoimNickname) like lower(concat('%', :q, '%'))) "
			+ "and (:linked is null or (:linked = true and r.user is not null) "
			+ "or (:linked = false and r.user is null))")
	Page<RosterMember> search(@Param("q") String q, @Param("linked") Boolean linked, Pageable pageable);

	// 주의: clearAutomatically로 영속성 컨텍스트가 비워진다. 같은 트랜잭션에서 이 리스너 뒤에 도는 탈퇴 리스너가 미리 읽어 둔 엔티티의 변경은 사라질 수 있다.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RosterMember r set r.user = null where r.user.id = :userId")
	int unlinkUser(@Param("userId") long userId);
}
