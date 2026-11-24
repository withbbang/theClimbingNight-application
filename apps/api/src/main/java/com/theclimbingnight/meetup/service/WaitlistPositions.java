package com.theclimbingnight.meetup.service;

import com.theclimbingnight.meetup.entity.MeetupApplication;
import java.util.List;

final class WaitlistPositions {

	private WaitlistPositions() {
	}

	/** 대기 순서(appliedAt, id)로 정렬된 대기자 목록에서 신청의 1부터 시작하는 순번. 목록에 없으면 null. */
	static Integer of(MeetupApplication application, List<MeetupApplication> waiting) {
		int index = waiting.indexOf(application);
		return index < 0 ? null : index + 1;
	}
}
