package com.theclimbingnight.membership.repository;

import com.theclimbingnight.membership.dto.MemberResponse;
import com.theclimbingnight.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MemberQueryRepository extends Repository<User, Long> {

	/** 활성 MEMBER·ADMIN을 앱 닉네임 오름차순으로 돌려준다. q는 앱 닉네임·소모임 닉네임 부분일치(대소문자 무시). */
	@Query(value = "select new com.theclimbingnight.membership.dto.MemberResponse("
			+ "u.id, u.nickname, u.role, r.id, r.somoimNickname) "
			+ "from User u left join RosterMember r on r.user = u "
			+ "where u.status = com.theclimbingnight.user.entity.UserStatus.ACTIVE "
			+ "and u.role in (com.theclimbingnight.user.entity.Role.MEMBER, com.theclimbingnight.user.entity.Role.ADMIN) "
			+ "and (:q is null or lower(u.nickname) like lower(concat('%', :q, '%')) "
			+ "or lower(r.somoimNickname) like lower(concat('%', :q, '%'))) "
			+ "order by u.nickname asc, u.id asc",
			countQuery = "select count(u) from User u left join RosterMember r on r.user = u "
			+ "where u.status = com.theclimbingnight.user.entity.UserStatus.ACTIVE "
			+ "and u.role in (com.theclimbingnight.user.entity.Role.MEMBER, com.theclimbingnight.user.entity.Role.ADMIN) "
			+ "and (:q is null or lower(u.nickname) like lower(concat('%', :q, '%')) "
			+ "or lower(r.somoimNickname) like lower(concat('%', :q, '%')))")
	Page<MemberResponse> search(@Param("q") String q, Pageable pageable);
}
