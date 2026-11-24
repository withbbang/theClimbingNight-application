import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';

import type { Icon } from 'phosphor-react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

import { useTheme } from '@/hooks/use-theme';

/** 빈 화면의 그림. 홀드 모양 위에서 아이콘이 매달린 듯 천천히 흔들린다. */
export function FloatingIcon({ icon: FloatIcon }: { icon: Icon }) {
  const { colors } = useTheme();
  const reduced = useReducedMotion();
  const swing = useSharedValue(0);

  useEffect(() => {
    if (reduced) {
      return;
    }
    swing.set(
      withRepeat(withTiming(1, { duration: 1800, easing: Easing.inOut(Easing.sin) }), -1, true),
    );
    return () => cancelAnimation(swing);
  }, [reduced, swing]);

  const holdStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${-6 + 6 * swing.get()}deg` }],
  }));
  const iconStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -4 * swing.get() }, { rotate: `${4 - 8 * swing.get()}deg` }],
  }));

  return (
    <View
      style={styles.box}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      <Animated.View style={[styles.hold, { backgroundColor: colors.accent }, holdStyle]} />
      <Animated.View style={iconStyle}>
        <FloatIcon size={44} color={colors.onAccent} weight="fill" />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { width: 104, height: 96, alignItems: 'center', justifyContent: 'center' },
  hold: {
    ...StyleSheet.absoluteFill,
    borderTopLeftRadius: 46,
    borderTopRightRadius: 34,
    borderBottomRightRadius: 52,
    borderBottomLeftRadius: 40,
  },
});
