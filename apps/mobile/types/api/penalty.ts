export interface PenaltyResponse {
  id: number;
  meetupId: number;
  meetupTitle: string;
  meetupStartsAt: string;
  type: 'LATE_CANCEL' | 'NO_SHOW';
  active: boolean;
  revokeReason: string | null;
}

/** 명부별 활성 블랙리스트 횟수. */
export interface PenaltySummaryResponse {
  rosterMemberId: number;
  somoimNickname: string;
  totalCount: number;
  recentCount: number;
}

export interface RosterPenaltiesResponse extends PenaltySummaryResponse {
  /** 취소된 기록도 들어 있다. */
  penalties: PenaltyResponse[];
}
