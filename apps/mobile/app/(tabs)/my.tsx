import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { UserIcon } from 'phosphor-react-native/src/icons/User';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';
import { signOut } from '@/lib/sign-out';
import type { Role } from '@/types/api/user';

const ROLE_LABELS: Record<Role, string> = {
  USER: '인증 전 회원',
  MEMBER: '동호회원',
  ADMIN: '운영진',
};

export default function MyScreen() {
  const router = useRouter();
  const session = useSession();
  const { space } = useTheme();

  if (session.status !== 'authenticated') {
    return (
      <Screen scroll bottomInset={false}>
        <Notice
          illustration={<FloatingIcon icon={UserIcon} />}
          title="로그인이 필요해요"
          description="내 정보와 참석 기록을 한곳에서 볼 수 있어요."
          actionLabel="로그인하기"
          onAction={() => router.push('/login')}
        />
      </Screen>
    );
  }
  const { nickname, role } = session.tokens.user;
  return (
    <Screen scroll bottomInset={false}>
      <View style={{ gap: space(2) }}>
        <FadeInItem index={0}>
          <FloatingIcon icon={UserIcon} />
        </FadeInItem>
        <FadeInItem index={1}>
          <AppText variant="display" accessibilityRole="header">
            {nickname}
          </AppText>
        </FadeInItem>
        <FadeInItem index={2}>
          <AppText tone="muted">{ROLE_LABELS[role]}</AppText>
        </FadeInItem>
        <FadeInItem index={3} style={{ marginTop: space(6) }}>
          <Button label="로그아웃" variant="secondary" onPress={() => void signOut()} />
        </FadeInItem>
      </View>
    </Screen>
  );
}
