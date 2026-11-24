import { View } from 'react-native';

import { useRouter, type Href } from 'expo-router';

import { UserIcon } from 'phosphor-react-native/src/icons/User';

import { MenuRow } from '@/components/my/MenuRow';
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

interface Menu {
  label: string;
  href: Href;
}

const MEMBER_MENUS: Menu[] = [
  { label: '내 참석률', href: '/attendance' },
  { label: '내 블랙리스트 기록', href: '/penalties' },
];
const USER_MENUS: Menu[] = [{ label: '동호회원 인증', href: '/membership' }];
const COMMON_MENUS: Menu[] = [
  { label: '문의', href: '/inquiries' },
  { label: '알림 설정', href: '/settings/notifications' },
  { label: '닉네임 변경', href: '/settings/nickname' },
  { label: '탈퇴', href: '/settings/withdraw' },
];

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
  const menus = [...(role === 'USER' ? USER_MENUS : MEMBER_MENUS), ...COMMON_MENUS];
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
      </View>
      <View style={{ marginTop: space(6) }}>
        {menus.map(({ label, href }, i) => (
          <MenuRow key={label} label={label} index={i + 3} onPress={() => router.push(href)} />
        ))}
      </View>
      <FadeInItem index={menus.length + 3} style={{ marginTop: space(6) }}>
        <Button label="로그아웃" variant="secondary" onPress={() => void signOut()} />
      </FadeInItem>
    </Screen>
  );
}
