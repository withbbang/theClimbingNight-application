import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { Hold } from '@/components/ui/Hold';
import { useTheme } from '@/hooks/use-theme';
import { capacityText } from '@/lib/meetup-status';

const MAX_HOLDS = 20;

interface CapacityHoldsProps {
  confirmed: number;
  waitlist: number;
  capacity: number | null;
}

/** 정원만큼 박힌 홀드가 확정된 사람만큼 켜진다. */
export function CapacityHolds({ confirmed, waitlist, capacity }: CapacityHoldsProps) {
  const { space } = useTheme();
  return (
    <View style={{ gap: space(3) }}>
      <View style={[styles.row, { gap: space(4) }]}>
        <AppText variant="label">{capacityText(confirmed, capacity)}</AppText>
        {waitlist > 0 ? <AppText variant="label">{`대기 ${waitlist}`}</AppText> : null}
      </View>
      {capacity !== null && capacity <= MAX_HOLDS ? (
        <View
          style={[styles.row, styles.wrap, { gap: space(2) }]}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        >
          {Array.from({ length: capacity }, (_, i) => (
            <Hold key={i} on={i < confirmed} size={20} index={i} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row' },
  wrap: { flexWrap: 'wrap' },
});
