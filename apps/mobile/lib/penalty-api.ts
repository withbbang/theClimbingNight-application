import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type {
  PenaltyResponse,
  PenaltySummaryResponse,
  RosterPenaltiesResponse,
} from '@/types/api/penalty';

export const penaltyApi = {
  mine() {
    return apiFetch<PenaltyResponse[]>('/penalties/me');
  },
  summaries(page: number, size: number) {
    return apiFetch<PageResponse<PenaltySummaryResponse>>(`/penalties?page=${page}&size=${size}`);
  },
  ofRoster(rosterMemberId: number) {
    return apiFetch<RosterPenaltiesResponse>(`/roster-members/${rosterMemberId}/penalties`);
  },
  revoke(id: number, reason: string) {
    return apiFetch<PenaltyResponse>(`/penalties/${id}/revoke`, {
      method: 'POST',
      body: { reason },
    });
  },
};
