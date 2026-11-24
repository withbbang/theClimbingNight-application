import { apiFetch } from '@/lib/api';
import type { DateKey } from '@/lib/kst';
import type {
  MeetupDetailResponse,
  MeetupSummaryResponse,
  MyApplicationResponse,
} from '@/types/api/meetup';

export const meetupApi = {
  list(from: DateKey, to: DateKey) {
    return apiFetch<MeetupSummaryResponse[]>(`/meetups?from=${from}&to=${to}`);
  },
  get(id: number) {
    return apiFetch<MeetupDetailResponse>(`/meetups/${id}`);
  },
  apply(id: number) {
    return apiFetch<MyApplicationResponse>(`/meetups/${id}/applications`, { method: 'POST' });
  },
  cancel(id: number, reason?: string) {
    return apiFetch<MyApplicationResponse>(`/meetups/${id}/applications/me/cancel`, {
      method: 'POST',
      body: reason ? { reason } : {},
    });
  },
};
