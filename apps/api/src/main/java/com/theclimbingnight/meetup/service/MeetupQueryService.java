package com.theclimbingnight.meetup.service;

import com.theclimbingnight.attendance.entity.Attendance;
import com.theclimbingnight.attendance.repository.AttendanceRepository;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.common.TimeZones;
import com.theclimbingnight.meetup.dto.MeetupApplicationAdminResponse;
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
import com.theclimbingnight.penalty.entity.PenaltyType;
import com.theclimbingnight.penalty.repository.PenaltyCount;
import com.theclimbingnight.penalty.repository.PenaltyRepository;
import com.theclimbingnight.penalty.service.PenaltyService;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import com.theclimbingnight.user.entity.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 벙 목록·상세 조회. 명단은 공개 벙이거나 운영진일 때만 내려준다. */
@Service
@Transactional(readOnly = true)
public class MeetupQueryService {

	private static final long MAX_RANGE_DAYS = 62;

	/** 확정(확정 순) → 대기(대기 순) → 취소·내보냄(취소 시각 순), 같으면 신청 ID 순. */
	private static final Comparator<MeetupApplication> ADMIN_ORDER = Comparator
			.comparingInt((MeetupApplication a) -> switch (a.getStatus()) {
				case CONFIRMED -> 0;
				case WAITLISTED -> 1;
				default -> 2;
			})
			.thenComparing(MeetupQueryService::adminOrderTime, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(MeetupApplication::getId);

	private final MeetupRepository meetupRepository;
	private final MeetupApplicationRepository applicationRepository;
	private final RosterMemberRepository rosterMemberRepository;
	private final AttendanceRepository attendanceRepository;
	private final PenaltyRepository penaltyRepository;
	private final PenaltyService penaltyService;

	public MeetupQueryService(MeetupRepository meetupRepository, MeetupApplicationRepository applicationRepository,
			RosterMemberRepository rosterMemberRepository, AttendanceRepository attendanceRepository,
			PenaltyRepository penaltyRepository, PenaltyService penaltyService) {
		this.meetupRepository = meetupRepository;
		this.applicationRepository = applicationRepository;
		this.rosterMemberRepository = rosterMemberRepository;
		this.attendanceRepository = attendanceRepository;
		this.penaltyRepository = penaltyRepository;
		this.penaltyService = penaltyService;
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

	/**
	 * 운영진용 신청자 전체. 확정(확정 순) → 대기(대기 순) → 취소·내보냄(취소 시각 순).
	 * 참석·이 벙의 활성 블랙리스트·명부별 블랙리스트 횟수는 각각 쿼리 한 번으로 읽는다.
	 */
	public List<MeetupApplicationAdminResponse> applications(long meetupId) {
		if (!meetupRepository.existsById(meetupId)) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		List<MeetupApplication> applications = applicationRepository.findAllWithRosterByMeetupId(meetupId).stream()
				.sorted(ADMIN_ORDER).toList();
		if (applications.isEmpty()) {
			return List.of();
		}
		Map<Long, Attendance> attendances = new HashMap<>();
		attendanceRepository.findAllByMeetupId(meetupId)
				.forEach(a -> attendances.put(a.getRosterMember().getId(), a));
		Set<Long> lateCancelPenalized = new HashSet<>();
		penaltyRepository.findAllByMeetupIdAndRevokedAtIsNull(meetupId).stream()
				.filter(p -> p.getType() == PenaltyType.LATE_CANCEL)
				.forEach(p -> lateCancelPenalized.add(p.getRosterMember().getId()));
		List<Long> rosterIds = applications.stream().map(a -> a.getRosterMember().getId()).toList();
		Map<Long, PenaltyCount> counts = new HashMap<>();
		penaltyRepository.countActiveByRosterIds(rosterIds, penaltyService.recentFrom())
				.forEach(c -> counts.put(c.rosterMemberId(), c));
		return applications.stream().map(a -> {
			RosterMember roster = a.getRosterMember();
			Attendance attendance = attendances.get(roster.getId());
			PenaltyCount count = counts.get(roster.getId());
			return new MeetupApplicationAdminResponse(a.getId(), roster.getId(), roster.getSomoimNickname(),
					roster.getUser() == null ? null : roster.getUser().getId(), a.getStatus(), a.getAppliedAt(),
					a.getConfirmedAt(), a.getCanceledAt(), a.isLateCancel(), a.getCancelReason(),
					attendance == null ? null : attendance.getId(),
					attendance == null ? null : attendance.getResult(),
					lateCancelPenalized.contains(roster.getId()),
					count == null ? 0 : count.total(), count == null ? 0 : count.recent());
		}).toList();
	}

	private static Instant adminOrderTime(MeetupApplication application) {
		return switch (application.getStatus()) {
			case CONFIRMED -> application.getConfirmedAt();
			case WAITLISTED -> application.getAppliedAt();
			default -> application.getCanceledAt();
		};
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
