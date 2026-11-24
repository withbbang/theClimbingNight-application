import { authApi } from '@/lib/auth-api';
import {
  clearRegisteredPushToken,
  getRegisteredPushToken,
  waitForPushRegistration,
} from '@/lib/push';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { cleanUpSession, signOut, waitForCleanUp } from '@/lib/sign-out';
import { signOutSocial } from '@/lib/social';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => {}),
  deleteItemAsync: jest.fn(async () => {}),
}));
jest.mock('@/lib/auth-api', () => ({ authApi: { logout: jest.fn() } }));
jest.mock('@/lib/push', () => ({
  getRegisteredPushToken: jest.fn(),
  clearRegisteredPushToken: jest.fn(),
  waitForPushRegistration: jest.fn(async () => {}),
}));
jest.mock('@/lib/social', () => ({ signOutSocial: jest.fn() }));

const logout = jest.mocked(authApi.logout);

beforeEach(async () => {
  logout.mockReset().mockResolvedValue(undefined);
  jest.mocked(signOutSocial).mockReset().mockResolvedValue(undefined);
  jest.mocked(getRegisteredPushToken).mockReset().mockResolvedValue(null);
  jest.mocked(clearRegisteredPushToken).mockReset().mockResolvedValue(undefined);
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

it('sends the registered push token on logout', async () => {
  jest.mocked(getRegisteredPushToken).mockResolvedValue('ExponentPushToken[x]');
  await signOut();
  expect(logout).toHaveBeenCalledWith('R1', 'ExponentPushToken[x]');
});

it('forgets the registered token on logout', async () => {
  await signOut();
  expect(clearRegisteredPushToken).toHaveBeenCalledTimes(1);
});

it('still logs out on the server when the stored push token cannot be read', async () => {
  jest.mocked(getRegisteredPushToken).mockRejectedValue(new Error('keychain'));
  await signOut();
  expect(logout).toHaveBeenCalledWith('R1', undefined);
});

it('tells the server only after a push registration in flight has finished', async () => {
  let finish = () => {};
  jest.mocked(waitForPushRegistration).mockReturnValueOnce(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const done = signOut();
  await new Promise((resolve) => setTimeout(resolve, 5));
  expect(logout).not.toHaveBeenCalled();
  finish();
  await done;
  expect(logout).toHaveBeenCalledTimes(1);
});

it('cleans up an expired session with its old refresh token', async () => {
  jest.mocked(getRegisteredPushToken).mockResolvedValue('ExponentPushToken[x]');
  await cleanUpSession('OLD');
  expect(logout).toHaveBeenCalledWith('OLD', 'ExponentPushToken[x]');
  expect(signOutSocial).toHaveBeenCalledTimes(1);
});

it('lets the next login wait for the device cleanup but not for the server', async () => {
  let finishSocial = () => {};
  jest.mocked(signOutSocial).mockReturnValue(
    new Promise((resolve) => {
      finishSocial = resolve;
    }),
  );
  logout.mockReturnValue(new Promise(() => {}));
  void signOut();
  let waited = false;
  const wait = waitForCleanUp().then(() => {
    waited = true;
  });
  await new Promise((resolve) => setTimeout(resolve, 5));
  expect(waited).toBe(false);
  finishSocial();
  await wait;
  expect(waited).toBe(true);
});

it('does not block the next login when a social SDK never answers', async () => {
  jest.useFakeTimers();
  try {
    jest.mocked(signOutSocial).mockReturnValue(new Promise(() => {}));
    void signOut();
    let waited = false;
    void waitForCleanUp().then(() => {
      waited = true;
    });
    await jest.advanceTimersByTimeAsync(3000);
    expect(waited).toBe(true);
  } finally {
    jest.useRealTimers();
  }
});
