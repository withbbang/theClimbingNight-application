import * as Notifications from 'expo-notifications';

import { act, renderHook } from '@testing-library/react-native';

import { usePush } from '@/hooks/use-push';
import { registerPushToken } from '@/lib/push';
import { queryClient } from '@/lib/query-client';
import { createSessionStore, sessionStore, type SessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

const mockSession: { current: SessionStore | null } = { current: null };
const mockNavigate = jest.fn();
const mockRouter = { canDismiss: () => false, navigate: mockNavigate };

jest.mock('@/lib/session-store', () => {
  const actual = jest.requireActual('@/lib/session-store');
  return {
    createSessionStore: actual.createSessionStore,
    get sessionStore() {
      return mockSession.current ?? actual.sessionStore;
    },
  };
});
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => {}),
  deleteItemAsync: jest.fn(async () => {}),
}));
jest.mock('expo-router', () => ({ useRouter: () => mockRouter }));
jest.mock('expo-notifications', () => ({
  useLastNotificationResponse: jest.fn(),
  clearLastNotificationResponse: jest.fn(),
  addNotificationReceivedListener: jest.fn(),
}));
jest.mock('@/lib/push', () => ({ registerPushToken: jest.fn(async () => {}) }));

const lastResponse = jest.mocked(Notifications.useLastNotificationResponse);
const onReceived = jest.mocked(Notifications.addNotificationReceivedListener);

function notification(type: string) {
  return { request: { content: { data: { type } } } } as unknown as Notifications.Notification;
}

function tap(type: string) {
  return { notification: notification(type) } as Notifications.NotificationResponse;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockSession.current = createSessionStore();
  lastResponse.mockReturnValue(null);
  onReceived.mockReturnValue({ remove: jest.fn() });
});

it('registers on start when logged in and again after login', async () => {
  await sessionStore.signIn(tokens('1'));
  await renderHook(() => usePush());
  expect(registerPushToken).toHaveBeenCalledTimes(1);
  await act(async () => {
    await sessionStore.updateTokens(tokens('2'), sessionStore.getGeneration());
  });
  expect(registerPushToken).toHaveBeenCalledTimes(1);
  await act(async () => sessionStore.signOut());
  await act(async () => sessionStore.signIn(tokens('3')));
  expect(registerPushToken).toHaveBeenCalledTimes(2);
});

it('opens the routed screen when a notification is tapped', async () => {
  await sessionStore.load();
  lastResponse.mockReturnValue(tap('MEETUP_CREATED'));
  await renderHook(() => usePush());
  expect(mockNavigate).toHaveBeenCalledWith('/meetups');
});

it('handles a notification response only once', async () => {
  await sessionStore.load();
  lastResponse.mockReturnValue(tap('MEETUP_CREATED'));
  const { rerender } = await renderHook(() => usePush());
  await rerender(undefined);
  expect(mockNavigate).toHaveBeenCalledTimes(1);
  expect(Notifications.clearLastNotificationResponse).toHaveBeenCalledTimes(1);
});

it('clears an unknown notification without moving', async () => {
  await sessionStore.load();
  lastResponse.mockReturnValue(tap('UNKNOWN'));
  await renderHook(() => usePush());
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(Notifications.clearLastNotificationResponse).toHaveBeenCalledTimes(1);
});

it('waits for the session to load before routing a cold start tap', async () => {
  lastResponse.mockReturnValue(tap('NOTICE_PUBLISHED'));
  await renderHook(() => usePush());
  expect(mockNavigate).not.toHaveBeenCalled();
  await act(async () => sessionStore.load());
  expect(mockNavigate).toHaveBeenCalledWith('/');
});

it('refreshes the user when a membership push arrives', async () => {
  const invalidate = jest.spyOn(queryClient, 'invalidateQueries');
  await renderHook(() => usePush());
  const receive = onReceived.mock.calls[0][0];
  receive(notification('MEETUP_CREATED'));
  expect(invalidate).not.toHaveBeenCalledWith({ queryKey: ['users', 'me'] });
  receive(notification('MEMBERSHIP_APPROVED'));
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 'me'] });
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['membership-requests'] });
});

it('refreshes the user when a membership push is tapped', async () => {
  const invalidate = jest.spyOn(queryClient, 'invalidateQueries');
  await sessionStore.load();
  lastResponse.mockReturnValue(tap('MEMBERSHIP_APPROVED'));
  await renderHook(() => usePush());
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 'me'] });
});
