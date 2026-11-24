package com.theclimbingnight.setting.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_settings")
public class AppSetting extends BaseTimeEntity {

	@Id
	@Column(name = "setting_key", length = 50)
	private String key;

	@Column(name = "setting_value", nullable = false, length = 500)
	private String value;

	public static AppSetting create(AppSettingKey key, String value) {
		AppSetting setting = new AppSetting();
		setting.key = key.dbKey();
		setting.value = value;
		return setting;
	}

	public void changeValue(String value) {
		this.value = value;
	}
}
