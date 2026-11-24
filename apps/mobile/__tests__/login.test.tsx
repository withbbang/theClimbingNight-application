import { AppState, Platform, type AppStateStatus } from 'react-native';

import { signInAsync } from 'expo-apple-authentication';
import { act, fireEvent, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { googleAdapter } from '@/lib/social/google';
import { kakaoAdapter } from '@/lib/social/kakao';
import { naverAdapter } from '@/lib/social/naver';
import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => {}),
  deleteItemAsync: jest.fn(async () => {}),
}));
jest.mock('expo-splash-screen', () => ({
  preventAutoHideAsync: jest.fn(async () => true),
  hideAsync: jest.fn(async () => {}),
}));
jest.mock('expo-haptics', () => ({
  selectionAsync: jest.fn(async () => {}),
  impactAsync: jest.fn(async () => {}),
  notificationAsync: jest.fn(async () => {}),
  ImpactFeedbackStyle: { Light: 'light' },
  NotificationFeedbackType: { Success: 'success', Error: 'error' },
}));
function mockAdapter(provider: string) {
  return { provider, isAvailable: jest.fn(), signIn: jest.fn(), signOut: jest.fn() };
}
jest.mock('@/lib/social/kakao', () => ({ kakaoAdapter: mockAdapter('KAKAO') }));
jest.mock('@/lib/social/naver', () => ({ naverAdapter: mockAdapter('NAVER') }));
jest.mock('@/lib/social/google', () => ({ googleAdapter: mockAdapter('GOOGLE') }));
jest.mock('expo-apple-authentication', () => {
  const { Pressable } = jest.requireActual('react-native');
  return {
    signInAsync: jest.fn(),
    AppleAuthenticationButton: ({ onPress }: { onPress: () => void }) => (
      <Pressable accessibilityRole="button" accessibilityLabel="Apple로 로그인" onPress={onPress} />
    ),
    AppleAuthenticationButtonType: { SIGN_IN: 0 },
    AppleAuthenticationButtonStyle: { WHITE: 0, BLACK: 2 },
  };
});

const adapters = [kakaoAdapter, naverAdapter, googleAdapter].map((a) => jest.mocked(a));
const kakaoSignIn = jest.mocked(kakaoAdapter.signIn);
const button = (name: string) => screen.getByRole('button', { name });
const socialButtons = () =>
  screen
    .getAllByRole('button')
    .map((b) => b.props.accessibilityLabel as string)
    .filter((label) => label.includes('로그인'));

async function press(name: string) {
  await fireEvent.press(button(name));
  await flush();
}

beforeEach(async () => {
  jest.replaceProperty(Platform, 'OS', 'ios');
  adapters.forEach((a) => {
    a.isAvailable.mockReturnValue(true);
    a.signIn.mockReset().mockResolvedValue({ type: 'success', token: 'social' });
  });
  queryClient.clear();
  await sessionStore.signOut();
});

afterEach(() => jest.restoreAllMocks());

it('lists kakao, naver, google and then apple on iOS', async () => {
  await renderApp('/login');
  expect(socialButtons()).toEqual([
    '카카오 로그인',
    '네이버 로그인',
    'Google로 로그인',
    'Apple로 로그인',
  ]);
});

it('hides apple on Android', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  await renderApp('/login');
  expect(socialButtons()).toEqual(['카카오 로그인', '네이버 로그인', 'Google로 로그인']);
});

it('explains when no provider is configured', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  adapters.forEach((a) => a.isAvailable.mockReturnValue(false));
  await renderApp('/login');
  expect(screen.getByText('로그인 설정이 아직 없어요')).toBeOnTheScreen();
});

it('signs in and returns to the tabs', async () => {
  const server = mockServer({
    '/auth/login': ok({ status: 'LOGGED_IN', tokens: tokens('1') }),
    '/users/me': ok(tokens('1').user),
  });
  const app = await renderApp('/login');
  await press('카카오 로그인');
  expect(server.calls[0]).toEqual({
    path: '/auth/login',
    body: { provider: 'KAKAO', token: 'social' },
  });
  expect(sessionStore.getState()).toEqual({ status: 'authenticated', tokens: tokens('1') });
  expect(app.getPathname()).toBe('/');
});

it('moves to signup when signup is required', async () => {
  mockServer({ '/auth/login': ok({ status: 'SIGNUP_REQUIRED', signupToken: 'secret-token' }) });
  const app = await renderApp('/login');
  await press('카카오 로그인');
  expect(app.getPathname()).toBe('/signup');
  expect(app.getPathnameWithParams()).not.toContain('secret-token');
});

it('shows no error when the user cancels', async () => {
  const server = mockServer({});
  kakaoSignIn.mockResolvedValue({ type: 'cancelled' });
  await renderApp('/login');
  await press('카카오 로그인');
  expect(screen.queryByRole('alert')).toBeNull();
  expect(button('카카오 로그인')).not.toBeDisabled();
  expect(server.calls).toHaveLength(0);
});

it('shows the server message on failure and clears it on the next try', async () => {
  let reply: Reply = fail(503, 'AUTH_PROVIDER_UNAVAILABLE', '로그인 서비스에 문제가 있습니다.');
  mockServer({ '/auth/login': () => reply });
  await renderApp('/login');
  await press('카카오 로그인');
  expect(screen.getByRole('alert')).toHaveTextContent('로그인 서비스에 문제가 있습니다.');
  reply = ok({ status: 'SIGNUP_REQUIRED', signupToken: 's' });
  kakaoSignIn.mockResolvedValue({ type: 'cancelled' });
  await press('카카오 로그인');
  expect(screen.queryByRole('alert')).toBeNull();
});

it('disables every button while one provider is signing in', async () => {
  let finish: (value: { type: 'cancelled' }) => void = () => {};
  kakaoSignIn.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  await renderApp('/login');
  await press('카카오 로그인');
  expect(button('카카오 로그인')).toBeBusy();
  expect(button('네이버 로그인')).toBeDisabled();
  expect(button('Google로 로그인')).toBeDisabled();
  await fireEvent.press(button('Apple로 로그인'));
  expect(signInAsync).not.toHaveBeenCalled();
  finish({ type: 'cancelled' });
  await flush();
  expect(button('네이버 로그인')).not.toBeDisabled();
});

it('stays on login when AUTH_INVALID_SOCIAL_TOKEN is returned', async () => {
  const server = mockServer({
    '/auth/login': fail(401, 'AUTH_INVALID_SOCIAL_TOKEN', '소셜 로그인을 확인하지 못했습니다.'),
  });
  const app = await renderApp('/login');
  await press('카카오 로그인');
  expect(app.getPathname()).toBe('/login');
  expect(server.count('/auth/refresh')).toBe(0);
  expect(screen.getByRole('alert')).toBeOnTheScreen();
});

it('goes back to the tabs with 둘러보기', async () => {
  const app = await renderApp('/login');
  await press('둘러보기');
  expect(app.getPathname()).toBe('/');
});

describe('when the social app never answers', () => {
  let changeState: (state: AppStateStatus) => void;
  let answer: (value: { type: 'success'; token: string } | { type: 'cancelled' }) => void;

  beforeEach(() => {
    const listeners = new Set<(state: AppStateStatus) => void>();
    jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, listener) => {
      listeners.add(listener);
      return { remove: () => listeners.delete(listener) } as ReturnType<
        typeof AppState.addEventListener
      >;
    });
    changeState = (state) => [...listeners].forEach((listener) => listener(state));
    kakaoSignIn.mockReturnValueOnce(
      new Promise((resolve) => {
        answer = resolve;
      }),
    );
  });

  async function comeBack() {
    await act(async () => {
      changeState('background');
      changeState('active');
      await jest.advanceTimersByTimeAsync(5000);
    });
  }

  it('unlocks the buttons after the user comes back', async () => {
    await renderApp('/login');
    await press('카카오 로그인');
    expect(button('네이버 로그인')).toBeDisabled();
    await comeBack();
    expect(button('네이버 로그인')).not.toBeDisabled();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('stays locked when only a system dialog covered the app', async () => {
    await renderApp('/login');
    await press('카카오 로그인');
    await act(async () => {
      changeState('inactive');
      changeState('active');
      await jest.advanceTimersByTimeAsync(5000);
    });
    expect(button('네이버 로그인')).toBeDisabled();
  });

  it('still accepts a late answer when nothing else was tried', async () => {
    mockServer({ '/auth/login': ok({ status: 'LOGGED_IN', tokens: tokens('1') }) });
    await renderApp('/login');
    await press('카카오 로그인');
    await comeBack();
    answer({ type: 'success', token: 'late' });
    await flush();
    expect(sessionStore.getState().status).toBe('authenticated');
  });

  it('ignores a late answer after another provider was tried', async () => {
    const server = mockServer({});
    jest.mocked(naverAdapter.signIn).mockReturnValue(new Promise(() => {}));
    await renderApp('/login');
    await press('카카오 로그인');
    await comeBack();
    await press('네이버 로그인');
    answer({ type: 'success', token: 'late' });
    await flush();
    expect(server.count('/auth/login')).toBe(0);
    expect(button('네이버 로그인')).toBeBusy();
  });

  it('ignores a late answer after leaving the login screen', async () => {
    const server = mockServer({});
    await renderApp('/login');
    await press('카카오 로그인');
    await press('둘러보기');
    answer({ type: 'success', token: 'late' });
    await flush();
    expect(server.count('/auth/login')).toBe(0);
  });
});
