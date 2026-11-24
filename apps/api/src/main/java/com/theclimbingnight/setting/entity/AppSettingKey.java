package com.theclimbingnight.setting.entity;

import java.util.Locale;

public enum AppSettingKey {

	SOMOIM_JOIN_URL, SMARTSTORE_URL;

	/** DB에 저장하는 키: enum 이름의 소문자. */
	public String dbKey() {
		return name().toLowerCase(Locale.ROOT);
	}
}
