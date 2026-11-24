import type { ReactNode } from 'react';

import { useRouter } from 'expo-router';

import { CalendarDotsIcon } from 'phosphor-react-native/src/icons/CalendarDots';
import { IdentificationCardIcon } from 'phosphor-react-native/src/icons/IdentificationCard';

import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { useSession } from '@/hooks/use-session';

interface RoleGateProps {
  role: 'USER' | 'MEMBER';
  children: ReactNode;
  /** 로그인 안내 문구. */
  title?: string;
  description?: string;
}

/** 등급에 따라 화면을 감춘다. 권한 판정은 API가 한다. */
export function RoleGate({
  role,
  children,
  title = '로그인이 필요해요',
  description = '로그인하면 이어서 볼 수 있어요.',
}: RoleGateProps) {
  const router = useRouter();
  const session = useSession();

  if (session.status !== 'authenticated') {
    return (
      <Notice
        illustration={<FloatingIcon icon={CalendarDotsIcon} />}
        title={title}
        description={description}
        actionLabel="로그인하기"
        onAction={() => router.push('/login')}
      />
    );
  }
  if (role === 'MEMBER' && session.tokens.user.role === 'USER') {
    return (
      <Notice
        illustration={<FloatingIcon icon={IdentificationCardIcon} />}
        title="동호회원 인증이 필요해요"
        description="인증을 마치면 벙 일정을 보고 신청할 수 있어요."
        actionLabel="인증하기"
        onAction={() => router.push('/membership')}
      />
    );
  }
  return children;
}
