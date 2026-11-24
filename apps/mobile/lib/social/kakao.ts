import { initializeKakaoSDK } from '@react-native-kakao/core';
import { login, logout } from '@react-native-kakao/user';

import { config } from '@/lib/config';

import { runSignIn, success, type SocialAdapter } from './types';

let initialized: Promise<void> | null = null;

function initialize() {
  initialized ??= Promise.resolve(initializeKakaoSDK(config.kakaoNativeAppKey ?? '')).catch(
    (error: unknown) => {
      initialized = null;
      throw error;
    },
  );
  return initialized;
}

export const kakaoAdapter: SocialAdapter = {
  provider: 'KAKAO',
  isAvailable: () => Boolean(config.kakaoNativeAppKey),
  signIn: () =>
    runSignIn(async () => {
      await initialize();
      const token = await login();
      return success(token.accessToken);
    }, ['Cancelled', 'AccessDenied']),
  async signOut() {
    await initialize();
    await logout();
  },
};
