import { useRouter } from 'expo-router';

import { CalendarXIcon } from 'phosphor-react-native/src/icons/CalendarX';

import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';

export function MeetupNotFound() {
  const router = useRouter();
  return (
    <Notice
      illustration={<FloatingIcon icon={CalendarXIcon} />}
      title="벙을 찾을 수 없어요"
      description="지워졌거나 주소가 잘못됐어요."
      actionLabel="홈으로"
      onAction={() => router.dismissTo('/')}
    />
  );
}
