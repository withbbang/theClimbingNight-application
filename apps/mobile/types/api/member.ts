import type { Role } from './user';

export interface MemberResponse {
  userId: number;
  nickname: string;
  role: Role;
  /** 명부와 연결되지 않았으면 null. */
  rosterMemberId: number | null;
  somoimNickname: string | null;
}
