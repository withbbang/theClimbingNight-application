import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { Hold } from '@/components/ui/Hold';

// 왼쪽 아래에서 오른쪽 위로 오르는 루트.
const ROUTE = [
  { x: 0, y: 44, size: 18 },
  { x: 34, y: 30, size: 24 },
  { x: 72, y: 34, size: 16 },
  { x: 98, y: 14, size: 22 },
  { x: 134, y: 0, size: 30 },
];

/** 홀드가 루트를 따라 하나씩 켜진다. */
export function HomeHeader() {
  return (
    <View style={styles.row}>
      <AppText variant="display" accessibilityRole="header" style={styles.title}>
        벽헤는밤
      </AppText>
      <View
        style={styles.route}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {ROUTE.map(({ x, y, size }, i) => (
          <View key={x} style={{ position: 'absolute', left: x, top: y }}>
            <Hold on size={size} index={i * 4} />
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  title: { flexShrink: 1 },
  route: { width: 164, height: 64 },
});
