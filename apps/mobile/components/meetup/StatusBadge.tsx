import { View } from 'react-native';

import Animated, { useReducedMotion, ZoomIn } from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Hold } from '@/components/ui/Hold';
import { useTheme } from '@/hooks/use-theme';
import type { MeetupApplicationStatus } from '@/types/api/meetup';

const LABELS: Partial<Record<MeetupApplicationStatus, string>> = {
  CONFIRMED: '확정',
  WAITLISTED: '대기',
};

/** 내 신청 상태. 확정은 켜진 홀드, 대기는 꺼진 홀드. */
export function StatusBadge({ status }: { status: MeetupApplicationStatus | null }) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const label = status ? LABELS[status] : undefined;
  if (!label) {
    return null;
  }
  return (
    <Animated.View key={status} entering={reduced ? undefined : ZoomIn.springify().damping(14)}>
      <View
        accessible
        accessibilityLabel={`내 상태: ${label}`}
        style={{ flexDirection: 'row', alignItems: 'center', gap: space(1.5) }}
      >
        <Hold on={status === 'CONFIRMED'} />
        <AppText variant="label">{label}</AppText>
      </View>
    </Animated.View>
  );
}
