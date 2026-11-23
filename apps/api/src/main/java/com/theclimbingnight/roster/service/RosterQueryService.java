package com.theclimbingnight.roster.service;

import com.theclimbingnight.common.PageResponse;
import com.theclimbingnight.roster.dto.RosterMemberResponse;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RosterQueryService {

	private final RosterMemberRepository rosterMemberRepository;

	public RosterQueryService(RosterMemberRepository rosterMemberRepository) {
		this.rosterMemberRepository = rosterMemberRepository;
	}

	/** 소모임 닉네임 부분일치(q)·연결 여부(linked)로 거른다. 둘 다 null이면 거르지 않는다. 닉네임 오름차순. */
	public PageResponse<RosterMemberResponse> search(String q, Boolean linked, int page, int size) {
		String keyword = q == null || q.isBlank() ? null : q.strip();
		Sort sort = Sort.by(Sort.Order.asc("somoimNickname"), Sort.Order.asc("id"));
		return PageResponse.from(rosterMemberRepository.search(keyword, linked, PageRequest.of(page, size, sort)),
				RosterMemberResponse::from);
	}
}
