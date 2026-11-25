import type { AttendanceResult } from './attendance';
import type { UserResponse } from './user';

export type MeetupStatus = 'OPEN' | 'CANCELED';
export type MeetupApplicationStatus = 'CONFIRMED' | 'WAITLISTED' | 'CANCELED' | 'REMOVED';

export interface MeetupSummaryResponse {
  id: number;
  title: string;
  placeName: string | null;
  startsAt: string;
  endsAt: string | null;
  capacity: number | null;
  confirmedCount: number;
  waitlistCount: number;
  status: MeetupStatus;
  myStatus: MeetupApplicationStatus | null;
}

export interface MeetupResponse {
  id: number;
  title: string;
  description: string | null;
  placeName: string | null;
  startsAt: string;
  endsAt: string | null;
  capacity: number | null;
  lateCancelHours: number;
  participantsVisible: boolean;
  status: MeetupStatus;
  canceledAt: string | null;
  host: UserResponse | null;
}

export interface MyApplicationResponse {
  applicationId: number;
  status: MeetupApplicationStatus;
  waitlistPosition: number | null;
  lateCancel: boolean;
  cancelReason: string | null;
}

export interface ParticipantResponse {
  somoimNickname: string;
  status: MeetupApplicationStatus;
  order: number;
}

export interface MeetupDetailResponse {
  meetup: MeetupResponse;
  confirmedCount: number;
  waitlistCount: number;
  my: MyApplicationResponse | null;
  /** 명단을 공개하지 않는 벙이면 null. */
  participants: ParticipantResponse[] | null;
}

/** 개설과 수정이 함께 쓴다. */
export interface MeetupWriteRequest {
  title: string;
  description: string | null;
  placeName: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  lateCancelHours: number;
  participantsVisible: boolean;
}

export interface MeetupCopyRequest {
  startsAt: string;
  endsAt: string;
}

/** 운영진 신청자 목록 한 줄. */
export interface MeetupApplicationAdminResponse {
  id: number;
  rosterMemberId: number;
  somoimNickname: string;
  /** 명부가 회원과 연결되지 않았으면 null. */
  userId: number | null;
  status: MeetupApplicationStatus;
  appliedAt: string;
  confirmedAt: string | null;
  canceledAt: string | null;
  lateCancel: boolean;
  cancelReason: string | null;
  /** 참석 기록이 없으면 null. */
  attendanceId: number | null;
  attendanceResult: AttendanceResult | null;
  /** 이 벙의 늦은 취소 블랙리스트가 있는가. */
  lateCancelPenalized: boolean;
  penaltyTotal: number;
  penaltyRecent: number;
}
