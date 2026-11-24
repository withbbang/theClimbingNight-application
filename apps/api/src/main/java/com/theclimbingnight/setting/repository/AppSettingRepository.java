package com.theclimbingnight.setting.repository;

import com.theclimbingnight.setting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
