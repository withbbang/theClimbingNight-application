import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type { MemberResponse } from '@/types/api/member';

export const memberApi = {
  list(q: string, page: number, size: number) {
    const query = q ? `q=${encodeURIComponent(q)}&` : '';
    return apiFetch<PageResponse<MemberResponse>>(`/members?${query}page=${page}&size=${size}`);
  },
  revoke(userId: number) {
    return apiFetch<void>(`/members/${userId}`, { method: 'DELETE' });
  },
};
