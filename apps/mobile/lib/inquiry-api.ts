import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type { InquiryAdminResponse, InquiryResponse } from '@/types/api/inquiry';

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
  all(status: InquiryResponse['status'], page: number, size: number) {
    return apiFetch<PageResponse<InquiryAdminResponse>>(
      `/inquiries?status=${status}&page=${page}&size=${size}`,
    );
  },
  get(id: number) {
    return apiFetch<InquiryAdminResponse>(`/inquiries/${id}`);
  },
  answer(id: number, answer: string) {
    return apiFetch<InquiryAdminResponse>(`/inquiries/${id}/answer`, {
      method: 'PUT',
      body: { answer },
    });
  },
};
