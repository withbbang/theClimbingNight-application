import { AppState, Linking } from 'react-native';

import { router } from 'expo-router';
import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';

import { act, fireEvent, screen } from 'expo-router/testing-library';

import { registerPushToken } from '@/lib/push';
import { appleAuthorizationCode } from '@/lib/social/apple';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

jest.mock('@/lib/social/apple', () => ({ appleAuthorizationCode: jest.fn() }));
jest.mock('@/lib/push', () => ({
  registerPushToken: jest.fn(async () => {}),
  getRegisteredPushToken: jest.fn(async () => null),
  clearRegisteredPushToken: jest.fn(async () => {}),
  waitForPushRegistration: jest.fn(async () => {}),
}));
jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  useLastNotificationResponse: jest.fn(() => null),
  clearLastNotificationResponse: jest.fn(),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  getPermissionsAsync: jest.fn(),
}));

const permissions = jest.mocked(Notifications.getPermissionsAsync);
const allow = (granted: boolean) =>
  permissions.mockResolvedValue({ granted } as Notifications.NotificationPermissionsStatus);

const SETTINGS = '/notification-settings/me';
const PUT = 'PUT ' + SETTINGS;
const ME = '/users/me';
const bothOn = ok({ newMeetup: true, newNotice: true });
const toggle = (name: string) => screen.getByRole('switch', { name });

async function flip(name: string) {
  await fireEvent.press(toggle(name));
  await flush();
}

beforeEach(async () => {
  allow(true);
  await startAs('MEMBER');
});

it('toggles one setting and sends both values', async () => {
  const server = mockServer({
    [SETTINGS]: bothOn,
    [PUT]: ok({ newMeetup: false, newNotice: true }),
  });
  await renderApp('/settings/notifications');
  await flip('새 벙');
  expect(server.calls.find((c) => c.method === 'PUT')?.body).toEqual({
    newMeetup: false,
    newNotice: true,
  });
  expect(toggle('새 벙')).not.toBeChecked();
  expect(toggle('새 공지')).toBeChecked();
});

it('restores the switch and shows the error when saving fails', async () => {
  mockServer({
    [SETTINGS]: bothOn,
    [PUT]: fail(500, 'COMMON_INTERNAL_ERROR', '서버에 문제가 있습니다.'),
  });
  await renderApp('/settings/notifications');
  await flip('새 벙');
  expect(toggle('새 벙')).toBeChecked();
  expect(screen.getByText('서버에 문제가 있습니다.')).toBeOnTheScreen();
});

it('ignores presses while saving', async () => {
  let finish = (_: Reply) => {};
  const server = mockServer({
    [SETTINGS]: bothOn,
    [PUT]: () => new Promise<Reply>((resolve) => (finish = resolve)),
  });
  await renderApp('/settings/notifications');
  await flip('새 벙');
  expect(toggle('새 벙')).not.toBeChecked();
  await flip('새 공지');
  expect(server.count(PUT)).toBe(1);
  await act(async () => finish(ok({ newMeetup: false, newNotice: true })));
});

it('offers the system settings when notifications are denied', async () => {
  allow(false);
  const open = jest.spyOn(Linking, 'openSettings').mockResolvedValue();
  mockServer({ [SETTINGS]: bothOn });
  await renderApp('/settings/notifications');
  await press('기기 설정 열기');
  expect(open).toHaveBeenCalled();
});

it('does not mention the system settings when notifications are allowed', async () => {
  mockServer({ [SETTINGS]: bothOn });
  await renderApp('/settings/notifications');
  expect(button('기기 설정 열기')).toBeNull();
});

it('changes the nickname everywhere', async () => {
  let nickname = '별이';
  const server = mockServer({
    [ME]: () => ok({ id: 1, nickname, role: 'MEMBER' }),
    ['PATCH ' + ME]: () => {
      nickname = '달이';
      return ok({ id: 1, nickname, role: 'MEMBER' });
    },
  });
  const app = await renderApp('/my');
  await press('닉네임 변경');
  await type('닉네임', ' 달이 ');
  await press('저장');
  expect(server.calls.find((c) => c.method === 'PATCH')?.body).toEqual({ nickname: '달이' });
  expect(app.getPathname()).toBe('/my');
  expect(screen.getByText('달이')).toBeOnTheScreen();
  const saved = jest.mocked(SecureStore.setItemAsync).mock.calls.at(-1)?.[1] ?? '';
  expect(JSON.parse(saved).user.nickname).toBe('달이');
});

it('is not undone by an older response of my info', async () => {
  let finish = (_: Reply) => {};
  mockServer({
    ['GET ' + ME]: () => new Promise<Reply>((resolve) => (finish = resolve)),
    ['PATCH ' + ME]: ok({ id: 1, nickname: '달이', role: 'MEMBER' }),
  });
  await renderApp('/settings/nickname');
  await type('닉네임', '달이');
  await press('저장');
  await act(async () => finish(ok({ id: 1, nickname: '별이', role: 'MEMBER' })));
  await flush();
  expect(sessionStore.getState()).toMatchObject({ tokens: { user: { nickname: '달이' } } });
});

it('shows a taken nickname under the field', async () => {
  mockServer({ ['PATCH ' + ME]: fail(409, 'USER_NICKNAME_TAKEN', '이미 사용 중인 닉네임입니다.') });
  await renderApp('/settings/nickname');
  await type('닉네임', '달이');
  await press('저장');
  expect(screen.getByRole('alert')).toHaveTextContent('이미 사용 중인 닉네임입니다.');
});

it.each(['별이', 'a b'])('does not send %p', async (value) => {
  const server = mockServer({});
  await renderApp('/settings/nickname');
  await type('닉네임', value);
  await press('저장');
  expect(server.count('PATCH ' + ME)).toBe(0);
});

it('needs the confirmation, then withdraws and signs out on this device', async () => {
  const server = mockServer({ ['DELETE ' + ME]: { status: 204 } });
  const app = await renderApp('/settings/withdraw');
  expect(button('탈퇴하기')).toBeDisabled();
  await flip('내용을 확인했어요');
  queryClient.setQueryData(['inquiries'], [1]);
  await press('탈퇴하기');
  expect(server.count('DELETE ' + ME)).toBe(1);
  expect(sessionStore.getState().status).toBe('guest');
  expect(queryClient.getQueryData(['inquiries'])).toBeUndefined();
  expect(app.getPathname()).toBe('/');
});

it('stays signed in when withdrawing fails', async () => {
  mockServer({ ['DELETE ' + ME]: fail(500, 'COMMON_INTERNAL_ERROR', '서버에 문제가 있습니다.') });
  await renderApp('/settings/withdraw');
  await flip('내용을 확인했어요');
  await press('탈퇴하기');
  expect(sessionStore.getState().status).toBe('authenticated');
  expect(screen.getByText('서버에 문제가 있습니다.')).toBeOnTheScreen();
});

const REAUTH = fail(400, 'AUTH_APPLE_REAUTH_REQUIRED', 'Apple 계정을 한 번 더 확인해야 탈퇴할 수 있어요.');

it('retries withdrawal with an Apple code when the server asks', async () => {
  let first = true;
  const server = mockServer({
    ['DELETE ' + ME]: () => {
      const reply = first ? REAUTH : { status: 204 };
      first = false;
      return reply;
    },
  });
  jest.mocked(appleAuthorizationCode).mockResolvedValue('code-1');
  await renderApp('/settings/withdraw');
  await flip('내용을 확인했어요');
  await press('탈퇴하기');
  const deletes = server.calls.filter((c) => c.method === 'DELETE');
  expect(deletes.map((c) => c.body)).toEqual([undefined, { appleAuthorizationCode: 'code-1' }]);
  expect(sessionStore.getState().status).toBe('guest');
});

it('stays on the screen without an error when Apple re-auth is cancelled', async () => {
  const server = mockServer({ ['DELETE ' + ME]: REAUTH });
  jest.mocked(appleAuthorizationCode).mockResolvedValue(null);
  await renderApp('/settings/withdraw');
  await flip('내용을 확인했어요');
  await press('탈퇴하기');
  expect(server.count('DELETE ' + ME)).toBe(1);
  expect(sessionStore.getState().status).toBe('authenticated');
  expect(screen.queryByText('Apple 계정을 한 번 더 확인해야 탈퇴할 수 있어요.')).toBeNull();
  expect(button('탈퇴하기')).toBeOnTheScreen();
});

it('registers this device once notifications are allowed in the system settings', async () => {
  allow(false);
  const listen = jest.mocked(AppState.addEventListener);
  listen.mockClear();
  mockServer({ [SETTINGS]: bothOn });
  await renderApp('/settings/notifications');
  expect(screen.getByRole('button', { name: '기기 설정 열기' })).toBeOnTheScreen();
  jest.mocked(registerPushToken).mockClear();
  allow(true);
  const becomeActive = () =>
    act(async () => listen.mock.calls.forEach(([type, on]) => type === 'change' && on('active')));
  await becomeActive();
  await flush();
  expect(button('기기 설정 열기')).toBeNull();
  await becomeActive();
  await flush();
  expect(registerPushToken).toHaveBeenCalledTimes(1);
});

it('does not navigate when the nickname screen was left before the response', async () => {
  let finish = (_: Reply) => {};
  mockServer({ ['PATCH ' + ME]: () => new Promise<Reply>((resolve) => (finish = resolve)) });
  const app = await renderApp('/settings/nickname');
  await type('닉네임', '달이');
  await press('저장');
  await act(async () => router.dismissTo('/goods'));
  await flush();
  await act(async () => finish(ok({ id: 1, nickname: '달이', role: 'MEMBER' })));
  await flush();
  expect(app.getPathname()).toBe('/goods');
});
