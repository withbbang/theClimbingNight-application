import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type { NoticeResponse, NoticeSummaryResponse } from '@/types/api/notice';

export const noticeApi = {
  list(page: number, size: number) {
    return apiFetch<PageResponse<NoticeSummaryResponse>>(`/notices?page=${page}&size=${size}`);
  },
  get(id: number) {
    return apiFetch<NoticeResponse>(`/notices/${id}`);
  },
};
