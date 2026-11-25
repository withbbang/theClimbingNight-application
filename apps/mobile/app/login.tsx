import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { LegalLinks } from '@/components/auth/LegalLinks';
import { RouteHolds } from '@/components/auth/RouteHolds';
import { SocialButtons } from '@/components/auth/SocialButtons';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';
import { useLogin } from '@/hooks/use-login';
import { useTheme } from '@/hooks/use-theme';
import { availableAdapters } from '@/lib/social';

export default function LoginScreen() {
  const router = useRouter();
  const { space } = useTheme();
  const { login, pending, error } = useLogin();
  const adapters = availableAdapters();

  return (
    <Screen scroll>
      <View style={styles.browse}>
        <Button
          label="둘러보기"
          variant="text"
          onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))}
        />
      </View>
      <RouteHolds />
      <View style={{ gap: space(2), marginBottom: space(6) }}>
        <FadeInItem index={1}>
          <AppText variant="display" accessibilityRole="header">
            벽헤는밤
          </AppText>
        </FadeInItem>
        <FadeInItem index={2}>
          <AppText tone="muted">벙 신청부터 참석 기록까지, 동호회 활동을 한곳에서.</AppText>
        </FadeInItem>
      </View>
      {error ? (
        <View style={{ marginBottom: space(4) }}>
          <ErrorBanner message={error} />
        </View>
      ) : null}
      {adapters.length > 0 ? (
        <SocialButtons
          adapters={adapters}
          pending={pending}
          onPress={(adapter) => void login(adapter)}
        />
      ) : (
        <Notice
          title="로그인 설정이 아직 없어요"
          description="소셜 로그인 키를 넣고 앱을 다시 빌드해 주세요."
        />
      )}
      <LegalLinks index={6} disabled={pending !== null} />
    </Screen>
  );
}

const styles = StyleSheet.create({ browse: { alignSelf: 'flex-end', marginRight: -12 } });
