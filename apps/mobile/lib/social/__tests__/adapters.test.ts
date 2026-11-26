import { Platform } from 'react-native';

import * as AppleAuthentication from 'expo-apple-authentication';

import { GoogleSignin } from '@react-native-google-signin/google-signin';
import * as KakaoCore from '@react-native-kakao/core';
import * as KakaoUser from '@react-native-kakao/user';
import NaverLogin from '@react-native-seoul/naver-login';

import { config, type AppConfig } from '@/lib/config';
import { availableAdapters, signOutSocial } from '@/lib/social';
import { appleAdapter, appleAuthorizationCode } from '@/lib/social/apple';
import { googleAdapter } from '@/lib/social/google';
import { kakaoAdapter } from '@/lib/social/kakao';
import { naverAdapter } from '@/lib/social/naver';
import { SocialSignInError } from '@/lib/social/types';

jest.mock('@/lib/config', () => ({ config: { apiUrl: 'https://api.test' } }));
jest.mock('@react-native-kakao/core', () => ({ initializeKakaoSDK: jest.fn() }));
jest.mock('@react-native-kakao/user', () => ({ login: jest.fn(), logout: jest.fn() }));
jest.mock('@react-native-seoul/naver-login', () => ({
  __esModule: true,
  default: { initialize: jest.fn(), login: jest.fn(), logout: jest.fn() },
}));
jest.mock('@react-native-google-signin/google-signin', () => ({
  GoogleSignin: {
    configure: jest.fn(),
    hasPlayServices: jest.fn(),
    signIn: jest.fn(),
    signOut: jest.fn(),
  },
}));
jest.mock('expo-apple-authentication', () => ({ signInAsync: jest.fn() }));

const kakaoLogin = jest.mocked(KakaoUser.login);
const naverLogin = jest.mocked(NaverLogin.login);
const googleSignIn = jest.mocked(GoogleSignin.signIn);
const appleSignIn = jest.mocked(AppleAuthentication.signInAsync);

function coded(code: string) {
  return Object.assign(new Error(code), { code });
}

function setConfig(next: Partial<AppConfig>) {
  Object.assign(config, {
    kakaoNativeAppKey: undefined,
    naver: undefined,
    google: undefined,
    ...next,
  });
}

beforeEach(() => {
  jest.resetAllMocks();
  jest.replaceProperty(Platform, 'OS', 'ios');
  setConfig({
    kakaoNativeAppKey: 'key',
    naver: { clientId: 'id', clientSecret: 'secret' },
    google: { webClientId: 'web', iosClientId: 'ios' },
  });
});

afterEach(() => jest.restoreAllMocks());

describe('kakao', () => {
  // SDK 초기화는 모듈 수명 동안 한 번이라 이 테스트를 맨 앞에 둔다.
  it('initializes the SDK once and again only after a failure', async () => {
    jest.mocked(KakaoCore.initializeKakaoSDK).mockRejectedValueOnce(new Error('x'));
    kakaoLogin.mockResolvedValue({ accessToken: 'a' } as KakaoUser.KakaoLoginToken);
    await expect(kakaoAdapter.signIn()).rejects.toThrow(SocialSignInError);
    await kakaoAdapter.signIn();
    await kakaoAdapter.signIn();
    expect(KakaoCore.initializeKakaoSDK).toHaveBeenCalledTimes(2);
    expect(KakaoCore.initializeKakaoSDK).toHaveBeenLastCalledWith('key');
  });

  it('returns the access token', async () => {
    kakaoLogin.mockResolvedValue({ accessToken: 'kakao-access' } as KakaoUser.KakaoLoginToken);
    await expect(kakaoAdapter.signIn()).resolves.toEqual({
      type: 'success',
      token: 'kakao-access',
    });
  });

  it.each(['Cancelled', 'AccessDenied'])('treats %s as cancellation', async (code) => {
    kakaoLogin.mockRejectedValue(coded(code));
    await expect(kakaoAdapter.signIn()).resolves.toEqual({ type: 'cancelled' });
  });
});

describe('naver', () => {
  it('returns the access token from successResponse', async () => {
    naverLogin.mockResolvedValue({
      isSuccess: true,
      successResponse: {
        accessToken: 'naver-access',
        refreshToken: '',
        expiresAtUnixSecondString: '',
        tokenType: '',
      },
    });
    await expect(naverAdapter.signIn()).resolves.toEqual({
      type: 'success',
      token: 'naver-access',
    });
    expect(NaverLogin.initialize).toHaveBeenCalledWith({
      appName: '벽헤는밤',
      consumerKey: 'id',
      consumerSecret: 'secret',
      serviceUrlSchemeIOS: 'theclimbingnightnaver',
    });
  });

  it('treats isCancel as cancellation', async () => {
    naverLogin.mockResolvedValue({
      isSuccess: false,
      failureResponse: { message: '', isCancel: true },
    });
    await expect(naverAdapter.signIn()).resolves.toEqual({ type: 'cancelled' });
  });

  it('throws when the failure is not a cancellation', async () => {
    naverLogin.mockResolvedValue({
      isSuccess: false,
      failureResponse: { message: 'x', isCancel: false },
    });
    await expect(naverAdapter.signIn()).rejects.toThrow(SocialSignInError);
  });
});

describe('google', () => {
  const success = (idToken: string | null) =>
    ({ type: 'success', data: { idToken } }) as Awaited<ReturnType<typeof GoogleSignin.signIn>>;

  it('returns the id token after forgetting the previous account', async () => {
    googleSignIn.mockResolvedValue(success('id'));
    await expect(googleAdapter.signIn()).resolves.toEqual({ type: 'success', token: 'id' });
    expect(GoogleSignin.configure).toHaveBeenCalledWith({
      webClientId: 'web',
      iosClientId: 'ios',
    });
    expect(GoogleSignin.signOut).toHaveBeenCalledTimes(1);
    expect(GoogleSignin.hasPlayServices).not.toHaveBeenCalled();
  });

  it('signs in even when forgetting the previous account fails', async () => {
    jest.mocked(GoogleSignin.signOut).mockRejectedValue(new Error('x'));
    googleSignIn.mockResolvedValue(success('id'));
    await expect(googleAdapter.signIn()).resolves.toEqual({ type: 'success', token: 'id' });
  });

  it('checks play services on Android', async () => {
    jest.replaceProperty(Platform, 'OS', 'android');
    googleSignIn.mockResolvedValue(success('id'));
    await googleAdapter.signIn();
    expect(GoogleSignin.hasPlayServices).toHaveBeenCalledWith({
      showPlayServicesUpdateDialog: true,
    });
  });

  it('treats a cancelled response as cancellation', async () => {
    googleSignIn.mockResolvedValue({ type: 'cancelled', data: null });
    await expect(googleAdapter.signIn()).resolves.toEqual({ type: 'cancelled' });
  });

  it('throws when the id token is null', async () => {
    googleSignIn.mockResolvedValue(success(null));
    await expect(googleAdapter.signIn()).rejects.toThrow(SocialSignInError);
  });
});

describe('apple', () => {
  it('returns the identity token', async () => {
    appleSignIn.mockResolvedValue({
      identityToken: 'apple-id',
    } as AppleAuthentication.AppleAuthenticationCredential);
    await expect(appleAdapter.signIn()).resolves.toEqual({ type: 'success', token: 'apple-id' });
  });

  it('treats ERR_REQUEST_CANCELED as cancellation', async () => {
    appleSignIn.mockRejectedValue(coded('ERR_REQUEST_CANCELED'));
    await expect(appleAdapter.signIn()).resolves.toEqual({ type: 'cancelled' });
  });

  it('returns the authorization code for withdrawal', async () => {
    appleSignIn.mockResolvedValue({
      authorizationCode: 'code-1',
    } as AppleAuthentication.AppleAuthenticationCredential);
    await expect(appleAuthorizationCode()).resolves.toBe('code-1');
  });

  it('returns null when the user closes the Apple sheet', async () => {
    appleSignIn.mockRejectedValue(coded('ERR_REQUEST_CANCELED'));
    await expect(appleAuthorizationCode()).resolves.toBeNull();
  });

  it('throws when Apple gives no authorization code', async () => {
    appleSignIn.mockResolvedValue({
      authorizationCode: null,
    } as AppleAuthentication.AppleAuthenticationCredential);
    await expect(appleAuthorizationCode()).rejects.toThrow(SocialSignInError);
  });

  it('is unavailable on Android', () => {
    expect(appleAdapter.isAvailable()).toBe(true);
    jest.replaceProperty(Platform, 'OS', 'android');
    expect(appleAdapter.isAvailable()).toBe(false);
  });
});

describe('all providers', () => {
  it.each([
    ['KAKAO', kakaoAdapter, () => kakaoLogin.mockRejectedValue(new Error('sdk'))],
    ['NAVER', naverAdapter, () => naverLogin.mockRejectedValue(new Error('sdk'))],
    ['GOOGLE', googleAdapter, () => googleSignIn.mockRejectedValue(new Error('sdk'))],
    ['APPLE', appleAdapter, () => appleSignIn.mockRejectedValue(new Error('sdk'))],
  ] as const)('wraps unknown %s failures in SocialSignInError', async (_name, adapter, fail) => {
    fail();
    await expect(adapter.signIn()).rejects.toThrow(
      new SocialSignInError('로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
    );
  });

  it('lists every provider in order when all are configured', () => {
    expect(availableAdapters().map((a) => a.provider)).toEqual([
      'KAKAO',
      'NAVER',
      'GOOGLE',
      'APPLE',
    ]);
  });

  it('lists only configured providers in order', () => {
    setConfig({ kakaoNativeAppKey: 'key' });
    expect(availableAdapters().map((a) => a.provider)).toEqual(['KAKAO', 'APPLE']);
  });

  it('continues signing out when one provider fails', async () => {
    jest.mocked(KakaoUser.logout).mockRejectedValue(new Error('not logged in'));
    await signOutSocial();
    expect(NaverLogin.logout).toHaveBeenCalledTimes(1);
    expect(GoogleSignin.signOut).toHaveBeenCalledTimes(1);
  });
});
