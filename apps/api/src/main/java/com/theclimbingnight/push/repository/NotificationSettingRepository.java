package com.theclimbingnight.push.repository;

import com.theclimbingnight.push.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	/** 탈퇴 리스너가 쓴다. 영속성 컨텍스트를 비우지 않는 벌크 삭제. */
	@Modifying
	@Query("delete from NotificationSetting s where s.userId = :userId")
	int deleteAllByUserId(@Param("userId") long userId);
}
