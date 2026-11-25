import { apiFetch } from '@/lib/api';
import type { DateKey } from '@/lib/kst';
import type {
  MeetupApplicationAdminResponse,
  MeetupCopyRequest,
  MeetupDetailResponse,
  MeetupResponse,
  MeetupSummaryResponse,
  MeetupWriteRequest,
  MyApplicationResponse,
} from '@/types/api/meetup';
import type { PenaltyResponse } from '@/types/api/penalty';

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
  create(body: MeetupWriteRequest) {
    return apiFetch<MeetupResponse>('/meetups', { method: 'POST', body });
  },
  update(id: number, body: MeetupWriteRequest) {
    return apiFetch<MeetupResponse>(`/meetups/${id}`, { method: 'PUT', body });
  },
  cancelMeetup(id: number) {
    return apiFetch<MeetupResponse>(`/meetups/${id}/cancel`, { method: 'POST' });
  },
  copy(id: number, body: MeetupCopyRequest) {
    return apiFetch<MeetupResponse>(`/meetups/${id}/copy`, { method: 'POST', body });
  },
  applications(id: number) {
    return apiFetch<MeetupApplicationAdminResponse[]>(`/meetups/${id}/applications`);
  },
  removeApplication(applicationId: number) {
    return apiFetch<void>(`/meetup-applications/${applicationId}/remove`, { method: 'POST' });
  },
  addLateCancelPenalty(applicationId: number) {
    return apiFetch<PenaltyResponse>(`/meetup-applications/${applicationId}/penalties`, {
      method: 'POST',
    });
  },
};
