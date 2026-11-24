package com.theclimbingnight.meetup.dto;

import jakarta.validation.constraints.Size;

/** 사유는 선택이다. 공백뿐이면 서비스에서 null로 저장한다. */
public record MeetupCancelApplicationRequest(@Size(max = 200) String reason) {
}
