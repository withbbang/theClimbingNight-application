import { authApi } from '@/lib/auth-api';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { signOut } from '@/lib/sign-out';
import { signOutSocial } from '@/lib/social';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => {}),
  deleteItemAsync: jest.fn(async () => {}),
}));
jest.mock('@/lib/auth-api', () => ({ authApi: { logout: jest.fn() } }));
jest.mock('@/lib/social', () => ({ signOutSocial: jest.fn() }));

const logout = jest.mocked(authApi.logout);

beforeEach(async () => {
  logout.mockReset().mockResolvedValue(undefined);
  jest.mocked(signOutSocial).mockReset().mockResolvedValue(undefined);
  queryClient.clear();
  await sessionStore.signIn(tokens('1'));
});

it('tells the server which session ended', async () => {
  await signOut();
  expect(logout).toHaveBeenCalledWith('R1', undefined);
  expect(signOutSocial).toHaveBeenCalledTimes(1);
});

it('clears the local session even when the server call fails', async () => {
  logout.mockRejectedValue(new Error('offline'));
  await signOut();
  expect(sessionStore.getState()).toEqual({ status: 'guest' });
});

it('clears the local session before waiting for the server', async () => {
  let statusAtCall = '';
  logout.mockImplementation(async () => {
    statusAtCall = sessionStore.getState().status;
  });
  await signOut();
  expect(statusAtCall).toBe('guest');
});

it('clears the query cache so the next account sees no old data', async () => {
  queryClient.setQueryData(['users', 'me'], { id: 1 });
  await signOut();
  expect(queryClient.getQueryData(['users', 'me'])).toBeUndefined();
});

it('skips the server call when already a guest', async () => {
  await sessionStore.signOut();
  await signOut();
  expect(logout).not.toHaveBeenCalled();
});
