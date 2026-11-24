package com.theclimbingnight.meetup.service;

import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 벙 일정 알림. 전날 20시부터 다음 날 벙까지 한 번 보내고, 서버가 꺼져 있어 놓쳤으면 당일 09시부터 시작 전까지 보낸다.
 * KST 09시 전에는 아무것도 하지 않는다. 클래스에 {@code @Transactional}을 붙이지 않는다. 벙마다
 * {@link MeetupReminderService#remind}가 자기 트랜잭션으로 커밋해야 한 벙이 실패해도 다른 벙이 막히지 않는다.
 */
@Component
public class MeetupReminderJob {

	private static final Logger log = LoggerFactory.getLogger(MeetupReminderJob.class);
	private static final int EARLIEST_HOUR = 9;
	private static final int EVENING_HOUR = 20;

	private final MeetupRepository meetupRepository;
	private final MeetupReminderService service;
	private final Clock clock;

	public MeetupReminderJob(MeetupRepository meetupRepository, MeetupReminderService service, Clock clock) {
		this.meetupRepository = meetupRepository;
		this.service = service;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 60_000)
	public void run() {
		Instant now = clock.instant();
		ZonedDateTime kst = now.atZone(TimeZones.KST);
		if (kst.getHour() < EARLIEST_HOUR) {
			return;
		}
		LocalDate today = kst.toLocalDate();
		LocalDate untilDate = kst.getHour() >= EVENING_HOUR ? today.plusDays(2) : today.plusDays(1);
		Instant until = untilDate.atStartOfDay(TimeZones.KST).toInstant();
		List<Long> dueIds = meetupRepository.findReminderDueIds(now, until);
		for (Long meetupId : dueIds) {
			try {
				service.remind(meetupId, until);
			} catch (RuntimeException e) {
				log.error("벙 일정 알림 실패 meetupId={} type={}", meetupId, e.getClass().getSimpleName());
			}
		}
	}
}
