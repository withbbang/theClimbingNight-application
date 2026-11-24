import type { ExpoConfig } from 'expo/config';

const APP_ID = 'com.theclimbingnight.app';
const NAVER_URL_SCHEME = 'theclimbingnightnaver';
const GOOGLE_CLIENT_ID_SUFFIX = '.apps.googleusercontent.com';

type Plugins = NonNullable<ExpoConfig['plugins']>;

/** 키가 없는 제공자의 plugin은 넣지 않는다 (빈 값이면 prebuild가 실패한다). */
function socialPlugins(env: NodeJS.ProcessEnv): Plugins {
  const plugins: Plugins = [];
  // 네이버를 카카오보다 앞에 둔다. 나중에 등록한 plugin이 먼저 실행되고, 네이버는 카카오가 넣은 코드를 고친다.
  if (env.EXPO_PUBLIC_NAVER_CLIENT_ID && env.EXPO_PUBLIC_NAVER_CLIENT_SECRET) {
    plugins.push(['@react-native-seoul/naver-login', { urlScheme: NAVER_URL_SCHEME }]);
  }
  const kakaoKey = env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY;
  if (kakaoKey) {
    plugins.push([
      '@react-native-kakao/core',
      {
        nativeAppKey: kakaoKey,
        android: { authCodeHandlerActivity: true },
        ios: { handleKakaoOpenUrl: true },
      },
    ]);
  }
  const googleIos = env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;
  if (env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID && googleIos) {
    const id = googleIos.replace(GOOGLE_CLIENT_ID_SUFFIX, '');
    plugins.push([
      '@react-native-google-signin/google-signin',
      { iosUrlScheme: `com.googleusercontent.apps.${id}` },
    ]);
  }
  return plugins;
}

export default function createConfig(): ExpoConfig {
  const env = process.env;
  return {
    name: '벽헤는밤',
    slug: 'theclimbingnight',
    scheme: 'theclimbingnight',
    version: '1.0.0',
    orientation: 'portrait',
    userInterfaceStyle: 'automatic',
    ios: {
      bundleIdentifier: APP_ID,
      usesAppleSignIn: true,
      supportsTablet: false,
    },
    android: {
      package: APP_ID,
      ...(env.GOOGLE_SERVICES_JSON ? { googleServicesFile: env.GOOGLE_SERVICES_JSON } : {}),
    },
    plugins: [
      'expo-router',
      'expo-secure-store',
      'expo-apple-authentication',
      ['expo-notifications', { color: '#FFC83D', defaultChannel: 'default' }],
      [
        'expo-build-properties',
        {
          android: {
            extraMavenRepos: ['https://devrepo.kakao.com/nexus/content/groups/public/'],
          },
        },
      ],
      [
        'expo-font',
        {
          fonts: ['Regular', 'SemiBold', 'ExtraBold'].map(
            (weight) => `./assets/fonts/Pretendard-${weight}.otf`,
          ),
        },
      ],
      ['expo-splash-screen', { backgroundColor: '#EEF2F8', dark: { backgroundColor: '#101A2E' } }],
      ...socialPlugins(env),
    ],
    experiments: { typedRoutes: true, reactCompiler: true },
    extra: { eas: { projectId: env.EAS_PROJECT_ID || undefined } },
  };
}
