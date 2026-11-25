import { readConfig } from '@/lib/config';

const API = { EXPO_PUBLIC_API_URL: 'https://a.test' };

it('throws when the API URL is missing', () => {
  expect(() => readConfig({})).toThrow('EXPO_PUBLIC_API_URL');
});

it('treats an empty API URL as missing', () => {
  expect(() => readConfig({ EXPO_PUBLIC_API_URL: '' })).toThrow('EXPO_PUBLIC_API_URL');
});

it('strips a trailing slash from the API URL', () => {
  expect(readConfig({ EXPO_PUBLIC_API_URL: 'https://a.test/' }).apiUrl).toBe('https://a.test');
});

it('leaves naver undefined when only the client id is set', () => {
  expect(readConfig({ ...API, EXPO_PUBLIC_NAVER_CLIENT_ID: 'x' }).naver).toBeUndefined();
});

it('reads every provider when all keys are set', () => {
  const config = readConfig({
    ...API,
    EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY: 'k',
    EXPO_PUBLIC_NAVER_CLIENT_ID: 'n',
    EXPO_PUBLIC_NAVER_CLIENT_SECRET: 's',
    EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID: 'w',
    EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID: 'i',
  });
  expect(config).toEqual({
    apiUrl: 'https://a.test',
    webUrl: 'https://a.test',
    kakaoNativeAppKey: 'k',
    naver: { clientId: 'n', clientSecret: 's' },
    google: { webClientId: 'w', iosClientId: 'i' },
  });
});

it('reads a separate web address', () => {
  const env = { ...API, EXPO_PUBLIC_WEB_URL: 'http://localhost:3000/' };
  expect(readConfig(env).webUrl).toBe('http://localhost:3000');
});

it('uses the API address when there is no web address', () => {
  expect(readConfig({ ...API, EXPO_PUBLIC_WEB_URL: '' }).webUrl).toBe('https://a.test');
});
