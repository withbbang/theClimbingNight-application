package com.theclimbingnight.attendance.service;

import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 끝났는데 아직 참석을 기록하지 않은 벙을 찾아 기록한다.
 * 클래스에 {@code @Transactional}을 붙이지 않는다. 벙마다 {@link AttendanceRecorder#record}가
 * 자기 트랜잭션으로 커밋해야 한 벙이 실패해도 다른 벙의 기록이 롤백되거나 막히지 않는다.
 */
@Component
public class AttendanceAutoRecordJob {

	private static final Logger log = LoggerFactory.getLogger(AttendanceAutoRecordJob.class);

	private final MeetupRepository meetupRepository;
	private final AttendanceRecorder recorder;
	private final Clock clock;

	public AttendanceAutoRecordJob(MeetupRepository meetupRepository, AttendanceRecorder recorder, Clock clock) {
		this.meetupRepository = meetupRepository;
		this.recorder = recorder;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 60_000)
	public void run() {
		for (Long meetupId : meetupRepository.findAttendanceDueIds(clock.instant())) {
			try {
				recorder.record(meetupId);
			} catch (RuntimeException e) {
				log.error("참석 자동 기록 실패 meetupId={}", meetupId, e);
			}
		}
	}
}
