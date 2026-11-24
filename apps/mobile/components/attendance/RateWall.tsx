import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Hold } from '@/components/ui/Hold';
import { useTheme } from '@/hooks/use-theme';

const HOLDS = 10;
const COUNT_STEPS = 12;
const COUNT_MS = 45;

/** 숫자가 0에서 올라간다. 마지막에는 서버 값 그대로다. */
function CountUp({ rate }: { rate: number }) {
  const reduced = useReducedMotion();
  const [step, setStep] = useState(0);
  const done = reduced || step >= COUNT_STEPS;

  useEffect(() => {
    if (done) {
      return;
    }
    const timer = setTimeout(() => setStep((s) => s + 1), COUNT_MS);
    return () => clearTimeout(timer);
  }, [done, step]);

  const shown = done ? rate : Math.round((rate * step) / COUNT_STEPS);
  return (
    <AppText variant="display" accessibilityLabel={`${rate}%`} style={styles.number}>
      {`${shown}%`}
    </AppText>
  );
}

/** 열 개의 홀드가 지그재그로 박힌 벽. 비율만큼 아래에서부터 켜지고 손이 따라 올라간다. */
export function RateWall({ rate }: { rate: number }) {
  const { motion } = useTheme();
  const reduced = useReducedMotion();
  const pop = useSharedValue(reduced ? 1 : 0);
  const lit = Math.round((rate / 100) * HOLDS);

  useEffect(() => {
    if (!reduced) {
      pop.set(withDelay(120, withSpring(1, motion.spring)));
    }
  }, [motion.spring, pop, reduced]);

  const animated = useAnimatedStyle(() => ({ transform: [{ scale: 0.7 + 0.3 * pop.get() }] }));

  return (
    <View style={styles.row}>
      <Animated.View style={[styles.left, animated]}>
        <CountUp rate={rate} />
      </Animated.View>
      <View
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={styles.wall}
      >
        {Array.from({ length: HOLDS }, (_, i) => {
          const order = HOLDS - 1 - i;
          return (
            <View key={order} style={{ marginLeft: [8, 58, 30, 88][order % 4] }}>
              <Hold on={order < lit} size={22} index={order * 2} />
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  left: { flexShrink: 1, transformOrigin: 'left' },
  number: { fontSize: 52, lineHeight: 62, letterSpacing: -1.6 },
  wall: { width: 124, gap: 4 },
});
