import { useEffect } from 'react';

import { DarkTheme, DefaultTheme, Stack, ThemeProvider, useRouter } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';

import { QueryClientProvider } from '@tanstack/react-query';
import { useReducedMotion } from 'react-native-reanimated';

import { usePush } from '@/hooks/use-push';
import { useSession } from '@/hooks/use-session';
import { useSyncMe } from '@/hooks/use-sync-me';
import { useTheme } from '@/hooks/use-theme';
import { setSessionExpiredHandler } from '@/lib/api';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { cleanUpSession } from '@/lib/sign-out';

// 푸시나 링크로 상세 화면을 바로 열어도 뒤로 가면 탭이 나온다.
export const unstable_settings = { anchor: '(tabs)' };

void SplashScreen.preventAutoHideAsync();

function Navigation() {
  const router = useRouter();
  const theme = useTheme();
  const reduced = useReducedMotion();
  useSyncMe();
  usePush();

  useEffect(() => {
    setSessionExpiredHandler((refreshToken) => {
      queryClient.clear();
      void cleanUpSession(refreshToken);
      router.navigate('/login');
    });
    return () => setSessionExpiredHandler(() => {});
  }, [router]);

  useEffect(() => {
    void SplashScreen.hideAsync();
  }, []);

  const base = theme.dark ? DarkTheme : DefaultTheme;
  return (
    <ThemeProvider
      value={{
        ...base,
        colors: {
          ...base.colors,
          background: theme.colors.background,
          card: theme.colors.surface,
          text: theme.colors.text,
          border: theme.colors.border,
          primary: theme.colors.accent,
        },
      }}
    >
      <StatusBar style="auto" />
      <Stack
        screenOptions={{
          headerShown: false,
          animation: reduced ? 'fade' : 'default',
          contentStyle: { backgroundColor: theme.colors.background },
        }}
      />
    </ThemeProvider>
  );
}

export default function RootLayout() {
  const session = useSession();

  useEffect(() => {
    void sessionStore.load();
  }, []);

  if (session.status === 'loading') {
    return null;
  }
  return (
    <QueryClientProvider client={queryClient}>
      <Navigation />
    </QueryClientProvider>
  );
}
