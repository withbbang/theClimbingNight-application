import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type { RosterMemberResponse } from '@/types/api/roster';

export const rosterApi = {
  /** 회원과 연결되지 않은 명부만 찾는다. */
  search(q: string, page: number, size: number) {
    return apiFetch<PageResponse<RosterMemberResponse>>(
      `/roster-members?q=${encodeURIComponent(q)}&linked=false&page=${page}&size=${size}`,
    );
  },
};
