import { CalendarDotsIcon } from 'phosphor-react-native/src/icons/CalendarDots';

import { RoleGate } from '@/components/auth/RoleGate';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';

export default function MeetupsScreen() {
  return (
    <Screen scroll bottomInset={false}>
      <RoleGate
        role="MEMBER"
        title="로그인하고 벙을 확인하세요"
        description="벙 일정과 신청은 동호회원에게 열려 있어요."
      >
        <Notice
          illustration={<FloatingIcon icon={CalendarDotsIcon} />}
          title="벙 일정을 준비하고 있어요"
          description="일정이 올라오면 이곳에서 신청할 수 있어요."
        />
      </RoleGate>
    </Screen>
  );
}
