export interface RosterCandidateResponse {
  id: number;
  somoimNickname: string;
  note: string | null;
  source: 'APP' | 'IMPORT';
}

export interface RosterMemberResponse extends RosterCandidateResponse {
  /** 회원과 연결되지 않았으면 null. */
  userId: number | null;
}
