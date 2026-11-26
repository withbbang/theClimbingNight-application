import { Platform } from 'react-native';

import * as AppleAuthentication from 'expo-apple-authentication';

import { runSignIn, success, type SocialAdapter } from './types';

export const appleAdapter: SocialAdapter = {
  provider: 'APPLE',
  isAvailable: () => Platform.OS === 'ios',
  signIn: () =>
    runSignIn(async () => {
      const credential = await AppleAuthentication.signInAsync();
      return success(credential.identityToken);
    }, ['ERR_REQUEST_CANCELED']),
  // Apple은 앱에서 로그아웃할 세션이 없다.
  async signOut() {},
};

/**
 * 탈퇴할 때 Apple 토큰을 폐기하려고 받는 authorization code. 서버가 요구할 때만 부른다.
 * 사용자가 창을 닫으면 null.
 */
export async function appleAuthorizationCode(): Promise<string | null> {
  const result = await runSignIn(async () => {
    const credential = await AppleAuthentication.signInAsync();
    return success(credential.authorizationCode);
  }, ['ERR_REQUEST_CANCELED']);
  return result.type === 'cancelled' ? null : result.token;
}
