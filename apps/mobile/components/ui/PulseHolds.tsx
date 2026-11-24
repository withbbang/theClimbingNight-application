import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';

import Animated, {
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

function PulseHold({ index, color }: { index: number; color: string }) {
  const reduced = useReducedMotion();
  const lift = useSharedValue(0);

  useEffect(() => {
    if (reduced) {
      return;
    }
    lift.set(
      withDelay(
        index * 120,
        withRepeat(
          withSequence(withTiming(1, { duration: 320 }), withTiming(0, { duration: 420 })),
          -1,
        ),
      ),
    );
    return () => cancelAnimation(lift);
  }, [index, lift, reduced]);

  const animated = useAnimatedStyle(() => ({
    transform: [{ translateY: -5 * lift.get() }, { scale: 1 + 0.15 * lift.get() }],
  }));

  return <Animated.View style={[styles.hold, { backgroundColor: color }, animated]} />;
}

/** 진행 중 표시. 홀드 세 개를 차례로 짚는다. */
export function PulseHolds({ color }: { color: string }) {
  return (
    <View
      style={styles.row}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {[0, 1, 2].map((index) => (
        <PulseHold key={index} index={index} color={color} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 7, height: 24, alignItems: 'center' },
  hold: {
    width: 9,
    height: 9,
    borderTopLeftRadius: 5,
    borderTopRightRadius: 3,
    borderBottomRightRadius: 5,
    borderBottomLeftRadius: 6,
  },
});
