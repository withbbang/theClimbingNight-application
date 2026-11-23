package com.theclimbingnight.roster.dto;

import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;

public record RosterMemberResponse(long id, String somoimNickname, String note, RosterSource source, Long userId) {

	public static RosterMemberResponse from(RosterMember member) {
		Long userId = member.getUser() == null ? null : member.getUser().getId();
		return new RosterMemberResponse(member.getId(), member.getSomoimNickname(), member.getNote(),
				member.getSource(), userId);
	}
}
