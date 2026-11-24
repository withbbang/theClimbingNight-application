import NaverLogin from '@react-native-seoul/naver-login';

import { config } from '@/lib/config';

import { CANCELLED, runSignIn, SocialSignInError, success, type SocialAdapter } from './types';

function initialize() {
  NaverLogin.initialize({
    appName: '벽헤는밤',
    consumerKey: config.naver?.clientId ?? '',
    consumerSecret: config.naver?.clientSecret ?? '',
    // app.config.ts의 NAVER_URL_SCHEME과 같아야 한다.
    serviceUrlSchemeIOS: 'theclimbingnightnaver',
  });
}

export const naverAdapter: SocialAdapter = {
  provider: 'NAVER',
  isAvailable: () => Boolean(config.naver),
  signIn: () =>
    runSignIn(async () => {
      initialize();
      const response = await NaverLogin.login();
      if (response.isSuccess) {
        return success(response.successResponse?.accessToken);
      }
      if (response.failureResponse?.isCancel) {
        return CANCELLED;
      }
      throw new SocialSignInError();
    }),
  async signOut() {
    initialize();
    await NaverLogin.logout();
  },
};
