import * as Notifications from 'expo-notifications';
import * as SplashScreen from 'expo-splash-screen';

import { router } from 'expo-router';
import { act, renderRouter, screen, fireEvent } from 'expo-router/testing-library';

import { setSessionExpiredHandler } from '@/lib/api';
import { networkError } from '@/lib/api-error';
import { authApi } from '@/lib/auth-api';
import { queryClient } from '@/lib/query-client';
import { createSessionStore, sessionStore, type SessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

const mockSession: { current: SessionStore | null } = { current: null };
jest.mock('@/lib/session-store', () => {
  const actual = jest.requireActual('@/lib/session-store');
  return {
    createSessionStore: actual.createSessionStore,
    // 테스트마다 앱을 새로 켠 상태로 시작한다.
    get sessionStore() {
      return mockSession.current ?? actual.sessionStore;
    },
  };
});

const mockStored = new Map<string, string>();
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async (key: string) => mockStored.get(key) ?? null),
  setItemAsync: jest.fn(async (key: string, value: string) => {
    mockStored.set(key, value);
  }),
  deleteItemAsync: jest.fn(async (key: string) => {
    mockStored.delete(key);
  }),
}));
jest.mock('expo-splash-screen', () => ({
  preventAutoHideAsync: jest.fn(async () => true),
  hideAsync: jest.fn(async () => {}),
}));
jest.mock('expo-haptics', () => ({
  selectionAsync: jest.fn(async () => {}),
  impactAsync: jest.fn(async () => {}),
  ImpactFeedbackStyle: { Light: 'light' },
}));
jest.mock('@/lib/auth-api', () => ({
  authApi: { getMe: jest.fn(), logout: jest.fn(async () => {}) },
}));
jest.mock('@/lib/social', () => ({
  availableAdapters: () => [],
  signOutSocial: jest.fn(async () => {}),
}));
jest.mock('@/lib/api', () => ({
  apiFetch: jest.fn(async () => []),
  setSessionExpiredHandler: jest.fn(),
}));

const getMe = jest.mocked(authApi.getMe);

async function start(session?: ReturnType<typeof tokens>, initialUrl = '/') {
  if (session) mockStored.set('session', JSON.stringify(session));
  const result = await renderRouter('./app', { initialUrl });
  await act(async () => {});
  return result;
}

beforeEach(() => {
  mockStored.clear();
  queryClient.clear();
  queryClient.setDefaultOptions({ queries: { retry: false } });
  jest.clearAllMocks();
  mockSession.current = createSessionStore();
  getMe.mockRejectedValue(networkError());
});

it('keeps the splash until the session is loaded', async () => {
  let finish = () => {};
  const load = jest.spyOn(sessionStore, 'load').mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  await renderRouter('./app', { initialUrl: '/' });
  expect(SplashScreen.hideAsync).not.toHaveBeenCalled();
  load.mockRestore();
  await act(async () => {
    await sessionStore.load();
    finish();
  });
  expect(SplashScreen.hideAsync).toHaveBeenCalledTimes(1);
});

it('shows the four tabs to a guest', async () => {
  await start();
  expect(screen.getAllByRole('tab').map((t) => t.props.accessibilityLabel)).toEqual([
    '홈',
    '벙',
    '굿즈',
    '마이',
  ]);
});

it('asks a guest to log in on the meetups tab', async () => {
  const app = await start(undefined, '/meetups');
  await fireEvent.press(screen.getByRole('button', { name: '로그인하기' }));
  expect(app.getPathname()).toBe('/login');
});

it('asks a USER for membership verification on the meetups tab', async () => {
  await start(tokens('1', 'USER'), '/meetups');
  expect(screen.getByText('동호회원 인증이 필요해요')).toBeOnTheScreen();
});

it.each(['MEMBER', 'ADMIN'] as const)('shows the meetups to %s', async (role) => {
  await start(tokens('1', role), '/meetups');
  expect(screen.getByText('다가오는 벙')).toBeOnTheScreen();
});

it('shows the nickname and logs out on the my tab', async () => {
  await start(tokens('1', 'USER', '별이'), '/my');
  expect(screen.getByText('별이')).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole('button', { name: '로그아웃' }));
  await act(async () => {});
  expect(screen.queryByText('별이')).toBeNull();
  expect(authApi.logout).toHaveBeenCalledWith('R1', undefined);
});

it('refreshes the cached user after start', async () => {
  getMe.mockResolvedValue({ id: 1, nickname: '별이', role: 'MEMBER' });
  await start(tokens('1', 'USER'), '/meetups');
  expect(await screen.findByText('다가오는 벙')).toBeOnTheScreen();
});

it('shows the server role again when a token refresh had changed it', async () => {
  getMe.mockResolvedValue({ id: 1, nickname: '별이', role: 'USER' });
  await start(tokens('1', 'USER'), '/meetups');
  // 시작할 때 받은 내 정보가 먼저 반영되게 한다.
  await act(async () => {
    await jest.advanceTimersByTimeAsync(20);
  });
  await act(async () => {
    await sessionStore.updateTokens(tokens('2', 'MEMBER'), sessionStore.getGeneration());
  });
  expect(screen.getByText('다가오는 벙')).toBeOnTheScreen();
  await act(async () => {
    await jest.advanceTimersByTimeAsync(10);
    await queryClient.refetchQueries({ queryKey: ['users', 'me'] });
  });
  expect(await screen.findByText('동호회원 인증이 필요해요')).toBeOnTheScreen();
});

it('leaves login when a session already exists', async () => {
  const app = await start(tokens('1'), '/my');
  await act(async () => router.push('/login'));
  await act(async () => {
    await jest.advanceTimersByTimeAsync(20);
  });
  expect(app.getPathname()).toBe('/my');
  expect(router.canDismiss()).toBe(false);
});

it('stays usable when getMe fails on the network', async () => {
  await start(tokens('1', 'USER', '별이'), '/my');
  expect(getMe).toHaveBeenCalled();
  expect(screen.getByText('별이')).toBeOnTheScreen();
});

it('opens login and clears cached queries when the session expires', async () => {
  const app = await start(tokens('1'), '/my');
  queryClient.setQueryData(['inquiries'], [1]);
  const handler = jest.mocked(setSessionExpiredHandler).mock.calls.at(-1)![0];
  await act(async () => {
    await sessionStore.signOut();
    handler('OLD');
  });
  expect(app.getPathname()).toBe('/login');
  expect(authApi.logout).toHaveBeenCalledWith('OLD', undefined);
  expect(queryClient.getQueryData(['inquiries'])).toBeUndefined();
});

const pushed = jest.mocked(Notifications.useLastNotificationResponse);

function tapPush(type: string) {
  pushed.mockReturnValue({
    notification: { request: { content: { data: { type } } } },
  } as unknown as Notifications.NotificationResponse);
}

afterEach(() => pushed.mockReturnValue(null));

it('opens the routed tab on a cold start', async () => {
  tapPush('MEETUP_CREATED');
  const app = await start(tokens('1', 'MEMBER'));
  expect(app.getPathname()).toBe('/meetups');
});

it('opens the routed tab when a push is tapped on another tab', async () => {
  const app = await start(tokens('1', 'MEMBER'), '/goods');
  tapPush('INQUIRY_ANSWERED');
  await act(async () => {
    await sessionStore.updateUser({ id: 1, nickname: '다시', role: 'MEMBER' });
  });
  expect(app.getPathname()).toBe('/my');
  expect(router.canDismiss()).toBe(false);
});

it('returns from login to the routed tab when a push is tapped', async () => {
  const app = await start(undefined, '/my');
  await act(async () => router.push('/login'));
  tapPush('MEETUP_CREATED');
  await act(async () => {
    await sessionStore.signIn(tokens('1', 'MEMBER'));
  });
  expect(app.getPathname()).toBe('/meetups');
  expect(router.canDismiss()).toBe(false);
});
