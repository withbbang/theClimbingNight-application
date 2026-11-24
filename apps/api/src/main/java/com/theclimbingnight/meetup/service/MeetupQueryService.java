package com.theclimbingnight.meetup.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.meetup.dto.MeetupDetailResponse;
import com.theclimbingnight.meetup.dto.MeetupResponse;
import com.theclimbingnight.meetup.dto.MeetupSummaryResponse;
import com.theclimbingnight.meetup.dto.MyApplicationResponse;
import com.theclimbingnight.meetup.dto.ParticipantResponse;
import com.theclimbingnight.meetup.entity.Meetup;
import com.theclimbingnight.meetup.entity.MeetupApplication;
import com.theclimbingnight.meetup.entity.MeetupApplicationStatus;
import com.theclimbingnight.meetup.repository.MeetupApplicationRepository;
import com.theclimbingnight.meetup.repository.MeetupRepository;
import com.theclimbingnight.meetup.repository.MeetupStatusCount;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 벙 목록·상세 조회. 명단은 공개 벙이거나 운영진일 때만 내려준다. */
@Service
@Transactional(readOnly = true)
public class MeetupQueryService {

	private static final long MAX_RANGE_DAYS = 62;

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final RosterMemberRepository rosterMemberRepository;

	public MeetupQueryService(MeetupRepository meetupRepository, MeetupApplicationRepository applicationRepository,
			RosterMemberRepository rosterMemberRepository) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.rosterMemberRepository = rosterMemberRepository;
	}

	/** 서울 기준 [from 00:00, to+1 00:00)에 시작하는 벙 (취소된 벙 포함). */
	public List<MeetupSummaryResponse> list(long userId, LocalDate from, LocalDate to) {
		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		Instant start = from.atStartOfDay(TimeZones.KST).toInstant();
		Instant end = to.plusDays(1).atStartOfDay(TimeZones.KST).toInstant();
		List<Meetup> meetups = meetupRepository
				.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(start, end);
		if (meetups.isEmpty()) {
			return List.of();
		}
		List<Long> ids = meetups.stream().map(Meetup::getId).toList();
		Map<Long, long[]> counts = countsByMeetup(ids);
		Map<Long, MeetupApplicationStatus> mine = new HashMap<>();
		Optional<RosterMember> roster = rosterMemberRepository.findByUserId(userId);
		if (roster.isPresent()) {
			applicationRepository.findAllByMeetupIdInAndRosterMemberId(ids, roster.get().getId())
					.forEach(a -> mine.put(a.getMeetup().getId(), a.getStatus()));
		}
		return meetups.stream().map(m -> {
			long[] count = counts.getOrDefault(m.getId(), new long[2]);
			return MeetupSummaryResponse.of(m, count[0], count[1], mine.get(m.getId()));
		}).toList();
	}

	public MeetupDetailResponse get(long meetupId, long userId, Role role) {
		Meetup meetup = meetupRepository.findById(meetupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		long[] count = countsByMeetup(List.of(meetupId)).getOrDefault(meetupId, new long[2]);
		Optional<MeetupApplication> mine = rosterMemberRepository.findByUserId(userId)
				.flatMap(roster -> applicationRepository.findByMeetupIdAndRosterMemberId(meetupId, roster.getId()));
		boolean showParticipants = meetup.isParticipantsVisible() || role == Role.ADMIN;
		boolean needWaitlist = showParticipants
				|| mine.map(a -> a.getStatus() == MeetupApplicationStatus.WAITLISTED).orElse(false);
		List<MeetupApplication> waiting = needWaitlist
				? applicationRepository.findAllWithRosterByMeetupIdAndStatusOrderByAppliedAtAscIdAsc(meetupId,
						MeetupApplicationStatus.WAITLISTED)
				: List.of();
		MyApplicationResponse my = mine.map(a -> MyApplicationResponse.of(a,
				a.getStatus() == MeetupApplicationStatus.WAITLISTED ? WaitlistPositions.of(a, waiting) : null))
				.orElse(null);
		List<ParticipantResponse> participants = null;
		if (showParticipants) {
			List<MeetupApplication> confirmed = applicationRepository
					.findAllWithRosterByMeetupIdAndStatusOrderByConfirmedAtAscIdAsc(meetupId,
							MeetupApplicationStatus.CONFIRMED);
			participants = java.util.stream.Stream.concat(numbered(confirmed), numbered(waiting)).toList();
		}
		return new MeetupDetailResponse(MeetupResponse.from(meetup), count[0], count[1], my, participants);
	}

	private static java.util.stream.Stream<ParticipantResponse> numbered(List<MeetupApplication> applications) {
		return IntStream.range(0, applications.size())
				.mapToObj(i -> ParticipantResponse.of(applications.get(i), i + 1));
	}

	/** 벙 ID별 [확정, 대기] 인원. */
	private Map<Long, long[]> countsByMeetup(List<Long> ids) {
		Map<Long, long[]> result = new HashMap<>();
		for (MeetupStatusCount c : applicationRepository.countByMeetupIds(ids)) {
			long[] slot = result.computeIfAbsent(c.meetupId(), k -> new long[2]);
			if (c.status() == MeetupApplicationStatus.CONFIRMED) {
				slot[0] = c.count();
			} else if (c.status() == MeetupApplicationStatus.WAITLISTED) {
				slot[1] = c.count();
			}
		}
		return result;
	}
}
