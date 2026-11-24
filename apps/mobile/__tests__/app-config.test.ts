import type { ExpoConfig } from 'expo/config';

import createConfig from '../app.config';

const original = process.env;

function load(env: Record<string, string>): ExpoConfig {
  process.env = { NODE_ENV: 'test', ...env };
  return createConfig();
}

function pluginNames(config: ExpoConfig) {
  return (config.plugins ?? []).map((p) => (Array.isArray(p) ? p[0] : p));
}

function pluginOptions(config: ExpoConfig, name: string) {
  const found = (config.plugins ?? []).find((p) => Array.isArray(p) && p[0] === name);
  return Array.isArray(found) ? found[1] : undefined;
}

afterEach(() => {
  process.env = original;
});

it('uses the fixed identifiers', () => {
  const config = load({});
  expect(config.ios?.bundleIdentifier).toBe('com.theclimbingnight.app');
  expect(config.android?.package).toBe('com.theclimbingnight.app');
  expect(config.name).toBe('벽헤는밤');
});

it('omits social plugins whose keys are missing', () => {
  const names = pluginNames(load({ EXPO_PUBLIC_NAVER_CLIENT_ID: 'only-id' }));
  expect(names).not.toContain('@react-native-kakao/core');
  expect(names).not.toContain('@react-native-seoul/naver-login');
  expect(names).not.toContain('@react-native-google-signin/google-signin');
});

it('adds the kakao plugin when the key is set', () => {
  const config = load({ EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY: 'k' });
  expect(pluginOptions(config, '@react-native-kakao/core')).toEqual({
    nativeAppKey: 'k',
    android: { authCodeHandlerActivity: true },
    ios: { handleKakaoOpenUrl: true },
  });
});

it('derives the google url scheme from the iOS client id', () => {
  const config = load({
    EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID: 'w',
    EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID: '123-abc.apps.googleusercontent.com',
  });
  expect(pluginOptions(config, '@react-native-google-signin/google-signin')).toEqual({
    iosUrlScheme: 'com.googleusercontent.apps.123-abc',
  });
});

it('sets the google services file only when it is provided', () => {
  expect(load({}).android?.googleServicesFile).toBeUndefined();
  expect(load({ GOOGLE_SERVICES_JSON: './g.json' }).android?.googleServicesFile).toBe('./g.json');
});

// config plugin은 나중에 등록한 것이 먼저 실행된다. 네이버 plugin은 카카오가 AppDelegate에 넣은 코드를
// 찾아 바꾸므로, 네이버를 앞에 둬야 두 로그인의 URL 처리가 모두 들어간다.
it('lists naver before kakao so that both URL handlers are generated', () => {
  const names = pluginNames(
    load({
      EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY: 'k',
      EXPO_PUBLIC_NAVER_CLIENT_ID: 'n',
      EXPO_PUBLIC_NAVER_CLIENT_SECRET: 's',
    }),
  );
  expect(names.indexOf('@react-native-seoul/naver-login')).toBeGreaterThan(-1);
  expect(names.indexOf('@react-native-seoul/naver-login')).toBeLessThan(
    names.indexOf('@react-native-kakao/core'),
  );
});
