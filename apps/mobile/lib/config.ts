export interface AppConfig {
  apiUrl: string;
  kakaoNativeAppKey?: string;
  naver?: { clientId: string; clientSecret: string };
  google?: { webClientId: string; iosClientId: string };
}

type Env = Record<string, string | undefined>;

export function readConfig(env: Env): AppConfig {
  const apiUrl = env.EXPO_PUBLIC_API_URL;
  if (!apiUrl) {
    throw new Error('EXPO_PUBLIC_API_URL 환경변수가 없습니다.');
  }
  const naverId = env.EXPO_PUBLIC_NAVER_CLIENT_ID;
  const naverSecret = env.EXPO_PUBLIC_NAVER_CLIENT_SECRET;
  const googleWeb = env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  const googleIos = env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;
  return {
    apiUrl: apiUrl.replace(/\/+$/, ''),
    kakaoNativeAppKey: env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY || undefined,
    naver: naverId && naverSecret ? { clientId: naverId, clientSecret: naverSecret } : undefined,
    google: googleWeb && googleIos ? { webClientId: googleWeb, iosClientId: googleIos } : undefined,
  };
}

// Expo는 process.env.EXPO_PUBLIC_X처럼 점으로 적은 것만 번들에 넣는다. 구조 분해하지 않는다.
export const config = readConfig({
  EXPO_PUBLIC_API_URL: process.env.EXPO_PUBLIC_API_URL,
  EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY: process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY,
  EXPO_PUBLIC_NAVER_CLIENT_ID: process.env.EXPO_PUBLIC_NAVER_CLIENT_ID,
  EXPO_PUBLIC_NAVER_CLIENT_SECRET: process.env.EXPO_PUBLIC_NAVER_CLIENT_SECRET,
  EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID,
  EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID,
});
