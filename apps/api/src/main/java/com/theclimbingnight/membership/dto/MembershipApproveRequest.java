package com.theclimbingnight.membership.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;

/** 기존 명부 항목(rosterMemberId)에 잇거나 새 항목을 만들거나(createNew) 둘 중 하나만 고른다. */
public record MembershipApproveRequest(Long rosterMemberId, boolean createNew) {

	/** JSON에서 createNew가 빠지거나 null이면 false로 본다 (원시 boolean은 null을 받으면 역직렬화가 실패한다). */
	@JsonCreator
	static MembershipApproveRequest fromJson(@JsonProperty("rosterMemberId") Long rosterMemberId,
			@JsonProperty("createNew") Boolean createNew) {
		return new MembershipApproveRequest(rosterMemberId, Boolean.TRUE.equals(createNew));
	}

	@AssertTrue(message = "rosterMemberId와 createNew 중 하나만 지정해야 합니다.")
	public boolean isExactlyOneChoice() {
		return rosterMemberId != null ^ createNew;
	}
}
