import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type { InquiryResponse } from '@/types/api/inquiry';

export const inquiryApi = {
  mine(page: number, size: number) {
    return apiFetch<PageResponse<InquiryResponse>>(`/inquiries/me?page=${page}&size=${size}`);
  },
  getMine(id: number) {
    return apiFetch<InquiryResponse>(`/inquiries/me/${id}`);
  },
  create(inquiry: { title: string; body: string }) {
    return apiFetch<InquiryResponse>('/inquiries', { method: 'POST', body: inquiry });
  },
};
