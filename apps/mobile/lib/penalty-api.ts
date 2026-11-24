import { apiFetch } from '@/lib/api';
import type { PenaltyResponse } from '@/types/api/penalty';

export const penaltyApi = {
  mine() {
    return apiFetch<PenaltyResponse[]>('/penalties/me');
  },
};
