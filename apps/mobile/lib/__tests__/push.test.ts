import { Platform } from 'react-native';

import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';

import { apiFetch } from '@/lib/api';
import { clearRegisteredPushToken, getRegisteredPushToken, registerPushToken } from '@/lib/push';
import { sessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

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
jest.mock('expo-device', () => ({ isDevice: true }));
jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { expoConfig: { extra: { eas: { projectId: 'p' } } } },
}));
jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  setNotificationChannelAsync: jest.fn(),
  getPermissionsAsync: jest.fn(),
  requestPermissionsAsync: jest.fn(),
  getExpoPushTokenAsync: jest.fn(),
  AndroidImportance: { DEFAULT: 3 },
}));
jest.mock('@/lib/api', () => ({ apiFetch: jest.fn() }));

const put = jest.mocked(apiFetch);
const getPermissions = jest.mocked(Notifications.getPermissionsAsync);
const requestPermissions = jest.mocked(Notifications.requestPermissionsAsync);
const getToken = jest.mocked(Notifications.getExpoPushTokenAsync);

function permission(granted: boolean, canAskAgain = true) {
  return { granted, canAskAgain } as Notifications.NotificationPermissionsStatus;
}

beforeEach(async () => {
  jest.clearAllMocks();
  put.mockReset();
  jest.replaceProperty(Platform, 'OS', 'ios');
  jest.replaceProperty(Device, 'isDevice', true);
  mockStored.clear();
  getPermissions.mockReset().mockResolvedValue(permission(true));
  requestPermissions.mockReset().mockResolvedValue(permission(true));
  getToken.mockReset().mockResolvedValue({ type: 'expo', data: 'ExponentPushToken[x]' });
  await sessionStore.signIn(tokens('1'));
});

afterEach(() => jest.restoreAllMocks());

it('creates the default Android channel before asking permission', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  getPermissions.mockResolvedValue(permission(false));
  const channel = jest.mocked(Notifications.setNotificationChannelAsync);
  await registerPushToken();
  expect(channel).toHaveBeenCalledWith('default', expect.objectContaining({ name: '알림' }));
  expect(channel.mock.invocationCallOrder[0]).toBeLessThan(
    requestPermissions.mock.invocationCallOrder[0],
  );
});

it('does nothing when permission is denied', async () => {
  getPermissions.mockResolvedValue(permission(false));
  requestPermissions.mockResolvedValue(permission(false, false));
  await registerPushToken();
  expect(put).not.toHaveBeenCalled();
});

it('does not ask again when permission cannot be asked', async () => {
  getPermissions.mockResolvedValue(permission(false, false));
  await registerPushToken();
  expect(requestPermissions).not.toHaveBeenCalled();
  expect(put).not.toHaveBeenCalled();
});

it('does nothing on a simulator', async () => {
  jest.replaceProperty(Device, 'isDevice', false);
  await registerPushToken();
  expect(put).not.toHaveBeenCalled();
});

it('does nothing without an EAS project id', async () => {
  jest.replaceProperty(Constants, 'expoConfig', null);
  await registerPushToken();
  expect(getPermissions).not.toHaveBeenCalled();
});

it('does nothing for a guest', async () => {
  await sessionStore.signOut();
  await registerPushToken();
  expect(getPermissions).not.toHaveBeenCalled();
});

it.each([
  ['ios', 'IOS'],
  ['android', 'ANDROID'],
] as const)('registers the token with the platform %s', async (os, platform) => {
  jest.replaceProperty(Platform, 'OS', os);
  await registerPushToken();
  expect(getToken).toHaveBeenCalledWith({ projectId: 'p' });
  expect(put).toHaveBeenCalledWith('/push-tokens', {
    method: 'PUT',
    body: { expoPushToken: 'ExponentPushToken[x]', platform },
  });
});

it('stores the registered token until it is cleared', async () => {
  await registerPushToken();
  await expect(getRegisteredPushToken()).resolves.toBe('ExponentPushToken[x]');
  await clearRegisteredPushToken();
  await expect(getRegisteredPushToken()).resolves.toBeNull();
});

it('has the token stored before the server call so an early logout can send it', async () => {
  let stored: string | null = null;
  put.mockImplementation(async () => {
    stored = await getRegisteredPushToken();
  });
  await registerPushToken();
  expect(stored).toBe('ExponentPushToken[x]');
});

it('swallows registration failures', async () => {
  put.mockRejectedValue(new Error('offline'));
  await expect(registerPushToken()).resolves.toBeUndefined();
  getToken.mockRejectedValue(new Error('offline'));
  await expect(registerPushToken()).resolves.toBeUndefined();
});

it('does not register when the user logged out while fetching the token', async () => {
  getToken.mockImplementation(async () => {
    await sessionStore.signOut();
    return { type: 'expo', data: 'ExponentPushToken[x]' };
  });
  await registerPushToken();
  expect(put).not.toHaveBeenCalled();
  expect(mockStored.has('expoPushToken')).toBe(false);
});
