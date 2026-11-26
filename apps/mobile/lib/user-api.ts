import { apiFetch } from '@/lib/api';
import type { UserResponse } from '@/types/api/user';

export const userApi = {
  changeNickname(nickname: string) {
    return apiFetch<UserResponse>('/users/me', { method: 'PATCH', body: { nickname } });
  },
  /** Apple로 가입한 사용자는 서버가 요구하면 재인증으로 받은 code를 싣는다. */
  withdraw(appleAuthorizationCode?: string) {
    return apiFetch<void>('/users/me', {
      method: 'DELETE',
      body: appleAuthorizationCode ? { appleAuthorizationCode } : undefined,
    });
  },
};
