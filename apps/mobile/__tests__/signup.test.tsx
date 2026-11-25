import { router } from 'expo-router';
import { act, fireEvent, screen } from 'expo-router/testing-library';

import { loginNotice } from '@/lib/login-notice';
import { pendingSignup } from '@/lib/pending-signup';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
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
jest.mock('@/lib/social', () => ({ availableAdapters: () => [], signOutSocial: jest.fn() }));

async function submit(nickname: string) {
  await fireEvent.changeText(screen.getByLabelText('닉네임'), nickname);
  await fireEvent.press(screen.getByRole('button', { name: '가입하기' }));
  await flush();
}

beforeEach(async () => {
  queryClient.clear();
  await sessionStore.signOut();
  pendingSignup.set('st');
});

it('returns to login when there is no pending signup token', async () => {
  pendingSignup.clear();
  const app = await renderApp('/signup');
  expect(app.getPathname()).toBe('/login');
});

it('creates the account and returns to the tabs', async () => {
  const server = mockServer({
    '/auth/signup': ok(tokens('1')),
    '/users/me': ok(tokens('1').user),
  });
  const app = await renderApp('/signup');
  await submit(' 가나 ');
  expect(server.calls.find((c) => c.method === 'POST')).toEqual({
    method: 'POST',
    path: '/auth/signup',
    body: { signupToken: 'st', nickname: '가나' },
  });
  expect(app.getPathname()).toBe('/');
  expect(sessionStore.getState().status).toBe('authenticated');
  expect(pendingSignup.get()).toBeNull();
});

it('shows the nickname rule without calling the server', async () => {
  const server = mockServer({});
  await renderApp('/signup');
  await submit('a b');
  expect(screen.getByRole('alert')).toHaveTextContent(
    '닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다.',
  );
  expect(server.calls.filter((c) => c.method === 'POST')).toHaveLength(0);
});

it('shows the taken-nickname message under the field', async () => {
  mockServer({
    '/auth/signup': fail(409, 'USER_NICKNAME_TAKEN', '이미 사용 중인 닉네임입니다.'),
  });
  const app = await renderApp('/signup');
  await submit('가나');
  expect(screen.getByRole('alert')).toHaveTextContent('이미 사용 중인 닉네임입니다.');
  expect(app.getPathname()).toBe('/signup');
  await fireEvent.changeText(screen.getByLabelText('닉네임'), '가나다');
  expect(screen.queryByRole('alert')).toBeNull();
});

it.each([
  ['the signup token expired', 400, 'AUTH_SIGNUP_EXPIRED', '가입 시간이 지났습니다.'],
  ['already registered', 409, 'AUTH_ALREADY_REGISTERED', '이미 가입된 계정입니다.'],
])('returns to login when %s', async (_name, status, code, message) => {
  mockServer({ '/auth/signup': fail(status, code, message) });
  const app = await renderApp('/signup');
  await submit('가나');
  expect(app.getPathname()).toBe('/login');
  expect(screen.getByRole('alert')).toHaveTextContent(message);
  expect(pendingSignup.get()).toBeNull();
});

it('keeps the token so the user can retry after a network failure', async () => {
  mockServer({});
  const app = await renderApp('/signup');
  await submit('가나');
  expect(app.getPathname()).toBe('/signup');
  expect(screen.getByRole('alert')).toHaveTextContent(
    '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
  );
  expect(pendingSignup.get()).toBe('st');
});

it('sends one request when submit is pressed twice', async () => {
  let finish: (reply: Reply) => void = () => {};
  const server = mockServer({
    '/auth/signup': () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
    '/users/me': ok(tokens('1').user),
  });
  await renderApp('/signup');
  await fireEvent.changeText(screen.getByLabelText('닉네임'), '가나');
  const button = screen.getByRole('button', { name: '가입하기' });
  await fireEvent.press(button);
  await fireEvent.press(button);
  await fireEvent(screen.getByLabelText('닉네임'), 'submitEditing');
  await flush();
  expect(server.count('/auth/signup')).toBe(1);
  await act(async () => finish(ok(tokens('1'))));
  await flush();
});

it('ignores a late result after leaving the screen', async () => {
  let finish: (reply: Reply) => void = () => {};
  mockServer({
    '/auth/signup': () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  });
  const app = await renderApp('/signup');
  await submit('가나');
  await act(async () => router.replace('/goods'));
  await act(async () => finish(fail(400, 'AUTH_SIGNUP_EXPIRED', '가입 시간이 지났습니다.')));
  await flush();
  expect(app.getPathname()).toBe('/goods');
  expect(loginNotice.take()).toBeNull();
});

it('keeps the nickname after reading the terms', async () => {
  mockServer({});
  const app = await renderApp('/signup');
  await fireEvent.changeText(screen.getByLabelText('닉네임'), '가나');
  await fireEvent.press(screen.getByRole('button', { name: '이용약관' }));
  await flush();
  expect(app.getPathname()).toBe('/legal/terms');
  await act(async () => router.back());
  expect(screen.getByLabelText('닉네임')).toHaveDisplayValue('가나');
});

it('leaves past the terms opened during signup', async () => {
  mockServer({ '/auth/signup': ok(tokens('1')), '/users/me': ok(tokens('1').user) });
  const app = await renderApp('/');
  await act(async () => router.push('/login'));
  await act(async () => router.push('/legal/terms'));
  await act(async () => router.push('/signup'));
  await submit('가나');
  expect(app.getPathname()).toBe('/');
});
