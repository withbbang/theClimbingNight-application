import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type { NoticeRequest, NoticeResponse, NoticeSummaryResponse } from '@/types/api/notice';

export const noticeApi = {
  list(page: number, size: number) {
    return apiFetch<PageResponse<NoticeSummaryResponse>>(`/notices?page=${page}&size=${size}`);
  },
  get(id: number) {
    return apiFetch<NoticeResponse>(`/notices/${id}`);
  },
  create(body: NoticeRequest) {
    return apiFetch<NoticeResponse>('/notices', { method: 'POST', body });
  },
  update(id: number, body: NoticeRequest) {
    return apiFetch<NoticeResponse>(`/notices/${id}`, { method: 'PUT', body });
  },
  remove(id: number) {
    return apiFetch<void>(`/notices/${id}`, { method: 'DELETE' });
  },
};
