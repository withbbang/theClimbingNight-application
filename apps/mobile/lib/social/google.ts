import { Platform } from 'react-native';

import { GoogleSignin } from '@react-native-google-signin/google-signin';

import { config } from '@/lib/config';

import { CANCELLED, runSignIn, success, type SocialAdapter } from './types';

function configure() {
  GoogleSignin.configure({
    webClientId: config.google?.webClientId,
    iosClientId: config.google?.iosClientId,
  });
}

async function signOut() {
  configure();
  await GoogleSignin.signOut();
}

export const googleAdapter: SocialAdapter = {
  provider: 'GOOGLE',
  isAvailable: () => Boolean(config.google),
  signIn: () =>
    runSignIn(async () => {
      configure();
      if (Platform.OS === 'android') {
        await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
      }
      // 이전 계정을 잊게 해 계정 선택 창이 항상 뜨게 한다.
      await Promise.resolve(GoogleSignin.signOut()).catch(() => {});
      const response = await GoogleSignin.signIn();
      return response.type === 'success' ? success(response.data.idToken) : CANCELLED;
    }),
  signOut,
};
