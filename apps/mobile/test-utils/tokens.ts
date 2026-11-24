import type { TokenResponse } from '@/types/api/auth';
import type { Role } from '@/types/api/user';

export function tokens(suffix: string, role: Role = 'USER', nickname = '별이'): TokenResponse {
  return {
    accessToken: `A${suffix}`,
    accessTokenExpiresAt: '2026-11-24T10:00:00Z',
    refreshToken: `R${suffix}`,
    user: { id: 1, nickname, role },
  };
}
