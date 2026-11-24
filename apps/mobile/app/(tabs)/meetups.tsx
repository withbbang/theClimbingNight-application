import { useRouter } from 'expo-router';

import { CalendarDotsIcon } from 'phosphor-react-native/src/icons/CalendarDots';
import { IdentificationCardIcon } from 'phosphor-react-native/src/icons/IdentificationCard';

import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';
import { useSession } from '@/hooks/use-session';

function MeetupsContent() {
  const router = useRouter();
  const session = useSession();

  if (session.status !== 'authenticated') {
    return (
      <Notice
        illustration={<FloatingIcon icon={CalendarDotsIcon} />}
        title="로그인하고 벙을 확인하세요"
        description="벙 일정과 신청은 동호회원에게 열려 있어요."
        actionLabel="로그인하기"
        onAction={() => router.push('/login')}
      />
    );
  }
  if (session.tokens.user.role === 'USER') {
    return (
      <Notice
        illustration={<FloatingIcon icon={IdentificationCardIcon} />}
        title="동호회원 인증이 필요해요"
        description="인증을 마치면 벙 일정을 보고 신청할 수 있어요."
      />
    );
  }
  return (
    <Notice
      illustration={<FloatingIcon icon={CalendarDotsIcon} />}
      title="벙 일정을 준비하고 있어요"
      description="일정이 올라오면 이곳에서 신청할 수 있어요."
    />
  );
}

export default function MeetupsScreen() {
  return (
    <Screen scroll bottomInset={false}>
      <MeetupsContent />
    </Screen>
  );
}
