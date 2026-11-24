import { apiFetch } from '@/lib/api';
import type { UserResponse } from '@/types/api/user';

export const userApi = {
  changeNickname(nickname: string) {
    return apiFetch<UserResponse>('/users/me', { method: 'PATCH', body: { nickname } });
  },
  withdraw() {
    return apiFetch<void>('/users/me', { method: 'DELETE' });
  },
};
