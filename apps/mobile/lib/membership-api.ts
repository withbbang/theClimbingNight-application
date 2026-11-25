import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type {
  BulkApproveResponse,
  MembershipApproveRequest,
  MembershipRequestResponse,
  MembershipReviewItemResponse,
} from '@/types/api/membership';

export const membershipApi = {
  /** 신청한 적이 없으면 204라 null. */
  async getMine() {
    const request = await apiFetch<MembershipRequestResponse | undefined>(
      '/membership-requests/me',
    );
    return request ?? null;
  },
  apply(somoimNickname: string) {
    return apiFetch<MembershipRequestResponse>('/membership-requests', {
      method: 'POST',
      body: { somoimNickname },
    });
  },
  pending(page: number, size: number) {
    return apiFetch<PageResponse<MembershipReviewItemResponse>>(
      `/membership-requests?status=PENDING&page=${page}&size=${size}`,
    );
  },
  approve(id: number, body: MembershipApproveRequest) {
    return apiFetch<MembershipRequestResponse>(`/membership-requests/${id}/approve`, {
      method: 'POST',
      body,
    });
  },
  reject(id: number, reason: string) {
    return apiFetch<MembershipRequestResponse>(`/membership-requests/${id}/reject`, {
      method: 'POST',
      body: { reason },
    });
  },
  bulkApprove() {
    return apiFetch<BulkApproveResponse>('/membership-requests/bulk-approve', { method: 'POST' });
  },
};
