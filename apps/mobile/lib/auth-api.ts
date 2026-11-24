import { apiFetch } from '@/lib/api';
import type { LoginResponse, SocialProvider, TokenResponse } from '@/types/api/auth';
import type { UserResponse } from '@/types/api/user';

export const authApi = {
  login(provider: SocialProvider, token: string) {
    return apiFetch<LoginResponse>('/auth/login', {
      method: 'POST',
      body: { provider, token },
      auth: false,
    });
  },
  signup(signupToken: string, nickname: string) {
    return apiFetch<TokenResponse>('/auth/signup', {
      method: 'POST',
      body: { signupToken, nickname },
      auth: false,
    });
  },
  logout(refreshToken: string, expoPushToken?: string) {
    return apiFetch<void>('/auth/logout', {
      method: 'POST',
      body: { refreshToken, ...(expoPushToken ? { expoPushToken } : {}) },
      auth: false,
    });
  },
  getMe() {
    return apiFetch<UserResponse>('/users/me');
  },
};
