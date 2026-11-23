package com.theclimbingnight.membership.dto;

import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;

public record RosterCandidateResponse(long id, String somoimNickname, String note, RosterSource source) {

	public static RosterCandidateResponse from(RosterMember member) {
		return new RosterCandidateResponse(member.getId(), member.getSomoimNickname(), member.getNote(),
				member.getSource());
	}
}
