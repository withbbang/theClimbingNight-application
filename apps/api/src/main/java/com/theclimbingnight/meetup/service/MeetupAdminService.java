package com.theclimbingnight.meetup.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.meetup.dto.MeetupResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupSource;
import com.theclimbingnight.meetup.entity.MeetupSpec;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영진의 벙 개설·수정·취소·복제. 수정·취소는 벙 잠금을 잡고 처리한다. */
@Service
@Transactional(readOnly = true)
public class MeetupAdminService {

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final WaitlistPromoter promoter;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public MeetupAdminService(MeetupRepository meetupRepository, MeetupApplicationRepository applicationRepository,
			UserRepository userRepository, WaitlistPromoter promoter, ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.userRepository = userRepository;
		this.promoter = promoter;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public MeetupResponse create(long adminId, MeetupSpec spec) {
		requireFuture(spec.startsAt());
		Meetup meetup = meetupRepository.save(Meetup.create(spec, userRepository.getReferenceById(adminId)));
		return MeetupResponse.from(meetup);
	}

	@Transactional
	public MeetupResponse update(long meetupId, MeetupSpec spec) {
		Meetup meetup = lock(meetupId);
		Instant now = clock.instant();
		if (!meetup.isOpen()) {
			throw new BusinessException(ErrorCode.MEETUP_CANCELED);
		}
		if (meetup.hasStarted(now)) {
			throw new BusinessException(ErrorCode.MEETUP_ALREADY_STARTED);
		}
		requireFuture(spec.startsAt());
		meetup.update(spec);
		promoter.promote(meetup, now);
		return MeetupResponse.from(meetup);
	}

	@Transactional
	public MeetupResponse cancel(long meetupId) {
		Meetup meetup = lock(meetupId);
		Instant now = clock.instant();
		if (!meetup.isOpen()) {
			throw new BusinessException(ErrorCode.MEETUP_CANCELED);
		}
		if (meetup.isOver(now)) {
			throw new BusinessException(ErrorCode.MEETUP_ENDED);
		}
		List<Long> roster = applicationRepository.findAllByMeetupId(meetupId).stream()
				.filter(MeetupApplication::isActive)
				.map(application -> application.getRosterMember().getId()).toList();
		meetup.cancel(now);
		eventPublisher.publishEvent(new MeetupCanceledEvent(meetupId, roster));
		return MeetupResponse.from(meetup);
	}

	/** 원본 상태와 무관하게 제목·설명·장소·정원 등을 복사하고 날짜만 바꾼다. 가져온 벙은 정원이 없어 복제할 수 없다. */
	@Transactional
	public MeetupResponse copy(long sourceId, long adminId, Instant startsAt, Instant endsAt) {
		Meetup source = meetupRepository.findById(sourceId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (source.getSource() != MeetupSource.APP) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		MeetupSpec original = source.toSpec();
		return create(adminId, new MeetupSpec(original.title(), original.description(), original.placeName(),
				startsAt, endsAt, original.capacity(), original.lateCancelHours(), original.participantsVisible()));
	}

	private Meetup lock(long meetupId) {
		return meetupRepository.findByIdForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	private void requireFuture(Instant startsAt) {
		if (!startsAt.isAfter(clock.instant())) {
			throw new BusinessException(ErrorCode.MEETUP_START_IN_PAST);
		}
	}
}
