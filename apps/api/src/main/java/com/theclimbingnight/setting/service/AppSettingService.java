package com.theclimbingnight.setting.service;

import com.theclimbingnight.setting.dto.AppSettingsResponse;
import com.theclimbingnight.setting.dto.AppSettingsUpdateRequest;
import com.theclimbingnight.setting.entity.AppSetting;
import com.theclimbingnight.setting.entity.AppSettingKey;
import com.theclimbingnight.setting.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppSettingService {

	private final AppSettingRepository appSettingRepository;

	public AppSettingService(AppSettingRepository appSettingRepository) {
		this.appSettingRepository = appSettingRepository;
	}

	public AppSettingsResponse get() {
		return response();
	}

	@Transactional
	public AppSettingsResponse update(AppSettingsUpdateRequest request) {
		apply(AppSettingKey.SOMOIM_JOIN_URL, request.somoimJoinUrl());
		apply(AppSettingKey.SMARTSTORE_URL, request.smartstoreUrl());
		appSettingRepository.flush();
		return response();
	}

	private void apply(AppSettingKey key, String value) {
		if (value == null) {
			appSettingRepository.deleteById(key.dbKey());
			return;
		}
		appSettingRepository.findById(key.dbKey()).ifPresentOrElse(setting -> setting.changeValue(value),
				() -> appSettingRepository.save(AppSetting.create(key, value)));
	}

	private AppSettingsResponse response() {
		return new AppSettingsResponse(value(AppSettingKey.SOMOIM_JOIN_URL), value(AppSettingKey.SMARTSTORE_URL));
	}

	private String value(AppSettingKey key) {
		return appSettingRepository.findById(key.dbKey()).map(AppSetting::getValue).orElse(null);
	}
}
