package com.theclimbingnight.penalty.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.penalty.dto.PenaltyResponse;
import com.theclimbingnight.penalty.dto.PenaltySummaryResponse;
import com.theclimbingnight.penalty.dto.RosterPenaltiesResponse;
import com.theclimbingnight.penalty.entity.Penalty;
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 블랙리스트 기록·취소·조회. 늦은 취소는 운영진이 직접 매기고, 노쇼는 참석 보정이 매긴다. */
@Service
@Transactional(readOnly = true)
public class PenaltyService {

	private static final int RECENT_MONTHS = 3;

	private final PenaltyRepository penaltyRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final MeetupRepository meetupRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public PenaltyService(PenaltyRepository penaltyRepository, MeetupApplicationRepository applicationRepository,
			MeetupRepository meetupRepository, RosterMemberRepository rosterMemberRepository,
			UserRepository userRepository, Clock clock) {
		this.penaltyRepository = penaltyRepository;
		this.applicationRepository = applicationRepository;
		this.meetupRepository = meetupRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/** "최근 3개월"의 시작: 지금을 서울 시각으로 바꿔 석 달 전 같은 시각. 벙 시작 시각이 이 값 이상이면 최근이다. */
	public Instant recentFrom() {
		return clock.instant().atZone(TimeZones.KST).minusMonths(RECENT_MONTHS).toInstant();
	}

	/**
	 * 잠금 순서(벙 → 신청)를 지키려고 신청의 벙 ID만 먼저 읽고, 벙과 신청을 차례로 잠근 뒤 활성 늦은 취소 기록이 없을 때만 저장한다.
	 * 신청만 잠그고 INSERT하면 FK 부모(벙)에 공유 잠금이 걸려 신청 → 벙 순서가 되어 재신청(벙 → 신청)과 교착할 수 있다.
	 */
	@Transactional
	public PenaltyResponse addLateCancel(long applicationId, long adminId) {
		long meetupId = applicationRepository.findMeetupIdById(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		meetupRepository.findByIdForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		MeetupApplication application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (!application.isLateCancel()) {
			throw new BusinessException(ErrorCode.PENALTY_NOT_LATE_CANCEL);
		}
		long rosterId = application.getRosterMember().getId();
		if (penaltyRepository.findActive(meetupId, rosterId, PenaltyType.LATE_CANCEL).isPresent()) {
			throw new BusinessException(ErrorCode.PENALTY_ALREADY_EXISTS);
		}
		Penalty penalty = penaltyRepository.save(Penalty.create(application.getRosterMember(),
				application.getMeetup(), PenaltyType.LATE_CANCEL, userRepository.getReferenceById(adminId)));
		return PenaltyResponse.from(penalty);
	}

	@Transactional
	public PenaltyResponse revoke(long penaltyId, long adminId, String reason) {
		Penalty penalty = penaltyRepository.findByIdForUpdate(penaltyId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (!penalty.isActive()) {
			throw new BusinessException(ErrorCode.PENALTY_ALREADY_REVOKED);
		}
		penalty.revoke(userRepository.getReferenceById(adminId), reason.strip(), clock.instant());
		return PenaltyResponse.from(penalty);
	}

	/** 호출자 명부의 기록 (취소된 기록 포함, 최신순). 명부가 없으면 빈 목록. */
	public List<PenaltyResponse> mine(long userId) {
		return rosterMemberRepository.findByUserId(userId)
				.map(roster -> penaltyRepository.findAllByRosterMemberIdOrderByIdDesc(roster.getId()).stream()
						.map(PenaltyResponse::from).toList())
				.orElse(List.of());
	}

	public RosterPenaltiesResponse ofRoster(long rosterMemberId) {
		RosterMember roster = rosterMemberRepository.findById(rosterMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		List<Penalty> penalties = penaltyRepository.findAllByRosterMemberIdOrderByIdDesc(rosterMemberId);
		Instant recentFrom = recentFrom();
		List<Penalty> active = penalties.stream().filter(Penalty::isActive).toList();
		long recent = active.stream().filter(p -> !p.getMeetup().getStartsAt().isBefore(recentFrom)).count();
		return new RosterPenaltiesResponse(roster.getId(), roster.getSomoimNickname(), active.size(), recent,
				penalties.stream().map(PenaltyResponse::from).toList());
	}

	public PageResponse<PenaltySummaryResponse> summaries(int page, int size) {
		return PageResponse.from(penaltyRepository.summarize(recentFrom(), PageRequest.of(page, size)),
				Function.identity());
	}
}
