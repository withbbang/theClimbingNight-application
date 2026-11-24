import { Platform } from 'react-native';

import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';

import { apiFetch } from '@/lib/api';
import { sessionStore } from '@/lib/session-store';

const KEY = 'expoPushToken';

let inFlight: Promise<unknown> = Promise.resolve();

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export function getRegisteredPushToken(): Promise<string | null> {
  return SecureStore.getItemAsync(KEY);
}

export function clearRegisteredPushToken(): Promise<void> {
  return SecureStore.deleteItemAsync(KEY);
}

/** 서버로 보낸 등록 요청이 끝날 때까지 기다린다. 실패해도 기다림은 끝난다. */
export async function waitForPushRegistration(): Promise<void> {
  await inFlight;
}

async function isPermitted() {
  const current = await Notifications.getPermissionsAsync();
  if (current.granted || !current.canAskAgain) {
    return current.granted;
  }
  return (await Notifications.requestPermissionsAsync()).granted;
}

async function register(projectId: string) {
  const generation = sessionStore.getGeneration();
  if (Platform.OS === 'android') {
    // Android 13부터 채널이 있어야 권한을 물을 수 있다.
    await Notifications.setNotificationChannelAsync('default', {
      name: '알림',
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }
  if (!(await isPermitted())) {
    return;
  }
  const { data: expoPushToken } = await Notifications.getExpoPushTokenAsync({ projectId });
  if (sessionStore.getGeneration() !== generation) {
    return;
  }
  // 서버에 보내기 전에 남겨야, 등록 중에 로그아웃해도 서버에서 지울 수 있다.
  await SecureStore.setItemAsync(KEY, expoPushToken);
  const sending = apiFetch<void>('/push-tokens', {
    method: 'PUT',
    body: { expoPushToken, platform: Platform.OS === 'ios' ? 'IOS' : 'ANDROID' },
  });
  inFlight = sending.catch(() => {});
  await sending;
}

/** 푸시는 없어도 앱을 쓸 수 있으므로 실패를 알리지 않는다. */
export async function registerPushToken(): Promise<void> {
  const projectId: unknown = Constants.expoConfig?.extra?.eas?.projectId;
  if (
    !Device.isDevice ||
    typeof projectId !== 'string' ||
    sessionStore.getState().status !== 'authenticated'
  ) {
    return;
  }
  try {
    await register(projectId);
  } catch {
    // 다음에 앱을 켜거나 로그인할 때 다시 등록한다.
  }
}
