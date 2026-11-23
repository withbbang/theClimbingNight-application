package com.theclimbingnight.membership.service;

import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 신청마다 이을 수 있는 명부 항목(후보)을 찾는다. 읽기만 한다. */
@Component
public class CandidateMatcher {

	private final RosterMemberRepository rosterMemberRepository;

	public CandidateMatcher(RosterMemberRepository rosterMemberRepository) {
		this.rosterMemberRepository = rosterMemberRepository;
	}

	/**
	 * 키는 신청 ID. 신청자가 이미 명부에 연결돼 있으면(자격 해제 후 재신청) 그 항목 하나만,
	 * 아니면 소모임 닉네임이 대소문자 무관하게 같은 미연결 항목들이 후보다.
	 */
	public Map<Long, List<RosterMember>> candidatesOf(Collection<MembershipRequest> requests) {
		if (requests.isEmpty()) {
			return Map.of();
		}
		Set<Long> userIds = new HashSet<>();
		for (MembershipRequest request : requests) {
			userIds.add(request.getUser().getId());
		}
		Map<Long, RosterMember> linkedByUserId = new HashMap<>();
		for (RosterMember member : rosterMemberRepository.findAllByUserIdIn(userIds)) {
			linkedByUserId.put(member.getUser().getId(), member);
		}

		Set<String> lowered = new LinkedHashSet<>();
		for (MembershipRequest request : requests) {
			if (!linkedByUserId.containsKey(request.getUser().getId())) {
				lowered.add(lower(request.getSomoimNickname()));
			}
		}
		Map<String, List<RosterMember>> unlinkedByNickname = new HashMap<>();
		if (!lowered.isEmpty()) {
			for (RosterMember member : rosterMemberRepository.findUnlinkedByLowerNicknames(lowered)) {
				unlinkedByNickname.computeIfAbsent(lower(member.getSomoimNickname()), key -> new ArrayList<>())
						.add(member);
			}
		}

		Map<Long, List<RosterMember>> result = new LinkedHashMap<>();
		for (MembershipRequest request : requests) {
			RosterMember linked = linkedByUserId.get(request.getUser().getId());
			result.put(request.getId(), linked != null ? List.of(linked)
					: List.copyOf(unlinkedByNickname.getOrDefault(lower(request.getSomoimNickname()), List.of())));
		}
		return result;
	}

	/**
	 * 후보가 정확히 하나이고, 그 명부 항목이 다른 어떤 대기 신청의 후보에도 없는 신청 ID.
	 * 같은 항목을 두 신청이 노리면 먼저 승인된 쪽이 남의 기록을 가져갈 수 있어 운영진이 직접 보게 한다.
	 */
	public Set<Long> bulkApprovableIds(Map<Long, List<RosterMember>> candidatesOfAllPending) {
		Map<Long, Integer> claimCount = new HashMap<>();
		for (List<RosterMember> candidates : candidatesOfAllPending.values()) {
			for (RosterMember member : candidates) {
				claimCount.merge(member.getId(), 1, Integer::sum);
			}
		}
		Set<Long> ids = new HashSet<>();
		candidatesOfAllPending.forEach((requestId, candidates) -> {
			if (candidates.size() == 1 && claimCount.get(candidates.get(0).getId()) == 1) {
				ids.add(requestId);
			}
		});
		return ids;
	}

	private static String lower(String nickname) {
		return nickname.strip().toLowerCase(Locale.ROOT);
	}
}
