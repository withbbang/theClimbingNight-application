import { apiFetch } from '@/lib/api';
import type { MembershipRequestResponse } from '@/types/api/membership';

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
};
