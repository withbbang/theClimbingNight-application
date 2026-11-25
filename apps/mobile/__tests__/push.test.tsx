import * as Notifications from 'expo-notifications';

import { act, screen } from 'expo-router/testing-library';

import { flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { detail } from '@/test-utils/meetups';
import { press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

jest.mock('@/lib/push', () => ({
  registerPushToken: jest.fn(async () => {}),
  getRegisteredPushToken: jest.fn(async () => null),
  clearRegisteredPushToken: jest.fn(async () => {}),
  waitForPushRegistration: jest.fn(async () => {}),
}));
const mockTap: { current: unknown; notify: () => void } = { current: null, notify: () => {} };
jest.mock('expo-notifications', () => {
  const { useSyncExternalStore } = jest.requireActual('react');
  return {
    addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
    useLastNotificationResponse: () =>
      useSyncExternalStore(
        (listener: () => void) => {
          mockTap.notify = listener;
          return () => {};
        },
        () => mockTap.current,
      ),
    clearLastNotificationResponse: () => {
      mockTap.current = null;
      mockTap.notify();
    },
  };
});

const response = (data: object) => ({ notification: { request: { content: { data } } } });

async function tap(data: object) {
  await act(async () => {
    mockTap.current = response(data);
    mockTap.notify();
  });
  await flush();
  await flush();
}

const server = () =>
  mockServer({
    '/meetups/3': ok(detail({ meetup: { id: 3, title: '셋째 벙' } })),
    '/meetups/7': ok(detail({ meetup: { id: 7, title: '일곱째 벙' } })),
  });

beforeEach(async () => {
  mockTap.current = null;
  await startAs('MEMBER');
});

it('opens the pushed meetup over another meetup detail', async () => {
  server();
  const app = await renderApp('/meetups/3');
  expect(screen.getByText('셋째 벙')).toBeOnTheScreen();
  await tap({ type: 'MEETUP_PROMOTED', meetupId: 7 });
  expect(app.getPathname()).toBe('/meetups/7');
  expect(screen.getByText('일곱째 벙')).toBeOnTheScreen();
  expect(screen.queryByText('셋째 벙')).toBeNull();
});

it('goes back to the tabs from a pushed detail on a cold start', async () => {
  server();
  mockTap.current = response({ type: 'MEETUP_PROMOTED', meetupId: 7 });
  const app = await renderApp('/');
  await flush();
  expect(app.getPathname()).toBe('/meetups/7');
  await press('뒤로');
  expect(app.getPathname()).toBe('/');
});

it('closes the login screen before opening the detail', async () => {
  server();
  const app = await renderApp('/login');
  await tap({ type: 'MEETUP_PROMOTED', meetupId: 7 });
  expect(app.getPathname()).toBe('/meetups/7');
  await press('뒤로');
  expect(app.getPathname()).not.toBe('/login');
});

it('opens a tab from a detail', async () => {
  server();
  const app = await renderApp('/meetups/3');
  await tap({ type: 'MEETUP_REMINDER' });
  expect(app.getPathname()).toBe('/meetups');
});

it('refetches the open detail when a push arrives in the foreground', async () => {
  const api = server();
  await renderApp('/meetups/7');
  const receive = jest.mocked(Notifications.addNotificationReceivedListener).mock.calls.at(-1)?.[0];
  await act(async () =>
    receive?.(
      response({ type: 'MEETUP_PROMOTED', meetupId: 7 })
        .notification as unknown as Notifications.Notification,
    ),
  );
  await flush();
  expect(api.count('/meetups/7')).toBe(2);
});
