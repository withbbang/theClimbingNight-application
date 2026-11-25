import { View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/kst';
import type { PenaltyResponse } from '@/types/api/penalty';

const TYPES = { LATE_CANCEL: '늦은 취소', NO_SHOW: '노쇼' } as const;

export function PenaltyRow({ penalty }: { penalty: PenaltyResponse }) {
  const { space } = useTheme();
  return (
    <View style={{ gap: space(1) }}>
      <AppText variant="label" tone={penalty.active ? 'default' : 'muted'}>
        {TYPES[penalty.type]}
      </AppText>
      <AppText>{penalty.meetupTitle}</AppText>
      <AppText variant="caption" tone="muted">
        {formatDateTime(penalty.meetupStartsAt)}
      </AppText>
      {penalty.active ? null : (
        <AppText variant="caption">
          {penalty.revokeReason ? `취소됨: ${penalty.revokeReason}` : '취소됨'}
        </AppText>
      )}
    </View>
  );
}
