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
