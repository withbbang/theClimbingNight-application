import { apiFetch } from '@/lib/api';
import { ApiError } from '@/lib/api-error';
import { authApi } from '@/lib/auth-api';
import { queryClient } from '@/lib/query-client';

jest.mock('@/lib/api', () => ({ apiFetch: jest.fn(async () => undefined) }));

beforeEach(() => jest.mocked(apiFetch).mockClear());

it('calls login, signup and logout without a bearer token', async () => {
  await authApi.login('KAKAO', 't');
  await authApi.signup('st', '가나');
  await authApi.logout('r');
  expect(jest.mocked(apiFetch).mock.calls).toEqual([
    ['/auth/login', { method: 'POST', body: { provider: 'KAKAO', token: 't' }, auth: false }],
    [
      '/auth/signup',
      { method: 'POST', body: { signupToken: 'st', nickname: '가나' }, auth: false },
    ],
    ['/auth/logout', { method: 'POST', body: { refreshToken: 'r' }, auth: false }],
  ]);
});

it('sends expoPushToken on logout only when given', async () => {
  await authApi.logout('r', 'ExponentPushToken[x]');
  expect(apiFetch).toHaveBeenCalledWith('/auth/logout', {
    method: 'POST',
    body: { refreshToken: 'r', expoPushToken: 'ExponentPushToken[x]' },
    auth: false,
  });
});

it('gets the current user with a bearer token', async () => {
  await authApi.getMe();
  expect(apiFetch).toHaveBeenCalledWith('/users/me');
});

it('retries server and network failures twice but never client errors', () => {
  const retry = queryClient.getDefaultOptions().queries?.retry as (n: number, e: Error) => boolean;
  expect(retry(0, new ApiError('AUTH_FORBIDDEN', '', 403))).toBe(false);
  expect(retry(1, new ApiError('CLIENT_NETWORK', '', 0))).toBe(true);
  expect(retry(2, new ApiError('CLIENT_NETWORK', '', 0))).toBe(false);
  expect(retry(0, new ApiError('COMMON_INTERNAL_ERROR', '', 500))).toBe(true);
});
