package com.theclimbingnight.push.entity;

import com.theclimbingnight.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 행이 없으면 모든 알림이 켜진 것으로 본다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_settings")
public class NotificationSetting extends BaseTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "new_meetup", nullable = false)
	private boolean newMeetup;

	@Column(name = "new_notice", nullable = false)
	private boolean newNotice;

	public static NotificationSetting create(long userId, boolean newMeetup, boolean newNotice) {
		NotificationSetting setting = new NotificationSetting();
		setting.userId = userId;
		setting.newMeetup = newMeetup;
		setting.newNotice = newNotice;
		return setting;
	}

	public void change(boolean newMeetup, boolean newNotice) {
		this.newMeetup = newMeetup;
		this.newNotice = newNotice;
	}
}
