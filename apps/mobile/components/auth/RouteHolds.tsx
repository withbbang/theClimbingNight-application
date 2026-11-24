import { useEffect } from 'react';
import { StyleSheet, View, type ViewStyle } from 'react-native';

import Animated, {
  cancelAnimation,
  Easing,
  interpolate,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

import { useTheme } from '@/hooks/use-theme';

interface Hold {
  left: `${number}%`;
  top: `${number}%`;
  width: number;
  height: number;
  rotate: number;
}

// 아래에서 위로 오르는 순서. 왼손, 오른손을 번갈아 짚는 동선이다.
const ROUTE: Hold[] = [
  { left: '8%', top: '86%', width: 58, height: 34, rotate: -12 },
  { left: '43%', top: '74%', width: 40, height: 40, rotate: 20 },
  { left: '20%', top: '56%', width: 48, height: 30, rotate: 8 },
  { left: '58%', top: '46%', width: 64, height: 36, rotate: -18 },
  { left: '34%', top: '29%', width: 36, height: 36, rotate: 34 },
  { left: '70%', top: '17%', width: 46, height: 32, rotate: -6 },
  { left: '50%', top: '1%', width: 72, height: 40, rotate: 10 },
];

// 길이 아닌 홀드. 밤하늘의 별처럼 흩어져 반짝인다.
const STARS: { left: `${number}%`; top: `${number}%`; size: number }[] = [
  { left: '4%', top: '10%', size: 8 },
  { left: '24%', top: '4%', size: 5 },
  { left: '15%', top: '35%', size: 6 },
  { left: '90%', top: '4%', size: 7 },
  { left: '88%', top: '38%', size: 5 },
  { left: '76%', top: '66%', size: 9 },
  { left: '92%', top: '82%', size: 6 },
  { left: '60%', top: '92%', size: 5 },
  { left: '3%', top: '66%', size: 5 },
  { left: '46%', top: '48%', size: 5 },
];

const STEP_MS = 190;
const START_MS = 350;
const BREATH_MS = 4200;

function blob(width: number, height: number): ViewStyle {
  return {
    width,
    height,
    borderTopLeftRadius: height * 0.55,
    borderTopRightRadius: height * 0.38,
    borderBottomRightRadius: height * 0.6,
    borderBottomLeftRadius: height * 0.45,
  };
}

interface RouteHoldProps {
  hold: Hold;
  index: number;
  /** 다 오른 뒤 아래에서 위로 흐르는 빛. 0에서 1로 되풀이한다. */
  breath: SharedValue<number>;
}

function RouteHold({ hold, index, breath }: RouteHoldProps) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const lit = useSharedValue(reduced ? 1 : 0);
  const entered = useSharedValue(reduced ? 1 : 0);

  useEffect(() => {
    if (!reduced) {
      entered.set(withDelay(index * 40, withSpring(1, motion.spring)));
      lit.set(withDelay(START_MS + index * STEP_MS, withSpring(1, motion.spring)));
    }
  }, [entered, index, lit, motion.spring, reduced]);

  const baseStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${hold.rotate}deg` }, { scale: entered.get() }],
  }));
  const litStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, lit.get())),
    transform: [{ scale: 0.6 + 0.4 * lit.get() }],
  }));
  const waveStyle = useAnimatedStyle(() => {
    const distance = Math.abs(breath.get() * (ROUTE.length + 2) - 1 - index);
    const glow = Math.max(0, 1 - distance);
    return { transform: [{ scale: 1 + 0.12 * glow }, { translateY: -3 * glow }] };
  });
  // 짚는 순간 분필 가루처럼 번지는 고리.
  const ringStyle = useAnimatedStyle(() => ({
    opacity: interpolate(lit.get(), [0, 0.25, 1], [0, 0.7, 0], 'clamp'),
    transform: [{ scale: 0.7 + 1.1 * lit.get() }],
  }));

  return (
    <View style={[styles.slot, { left: hold.left, top: hold.top }]}>
      <Animated.View style={waveStyle}>
        <Animated.View
          style={[blob(hold.width, hold.height), { backgroundColor: colors.holdOff }, baseStyle]}
        >
          <Animated.View
            style={[
              StyleSheet.absoluteFill,
              blob(hold.width, hold.height),
              { borderWidth: 2, borderColor: colors.accent },
              ringStyle,
            ]}
          />
          <Animated.View
            style={[
              StyleSheet.absoluteFill,
              blob(hold.width, hold.height),
              styles.center,
              { backgroundColor: colors.accent },
              litStyle,
            ]}
          >
            <View style={[styles.bolt, { backgroundColor: colors.onAccent }]} />
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

function Star({ left, top, size, index }: (typeof STARS)[number] & { index: number }) {
  const { colors } = useTheme();
  const reduced = useReducedMotion();
  const twinkle = useSharedValue(0);

  useEffect(() => {
    if (reduced) {
      return;
    }
    twinkle.set(
      withDelay(
        index * 260,
        withRepeat(
          withSequence(
            withTiming(1, { duration: 900, easing: Easing.inOut(Easing.sin) }),
            withTiming(0, { duration: 1300, easing: Easing.inOut(Easing.sin) }),
          ),
          -1,
        ),
      ),
    );
    return () => cancelAnimation(twinkle);
  }, [index, reduced, twinkle]);

  const animated = useAnimatedStyle(() => ({
    opacity: 0.35 + 0.65 * twinkle.get(),
    transform: [{ scale: 0.8 + 0.5 * twinkle.get() }],
  }));

  return (
    <Animated.View
      style={[
        styles.slot,
        {
          left,
          top,
          width: size,
          height: size,
          borderRadius: size,
          backgroundColor: colors.border,
        },
        animated,
      ]}
    />
  );
}

/** 로그인 화면의 벽. 홀드가 아래에서 위로 켜지고, 다 켜지면 빛이 길을 따라 흐른다. */
export function RouteHolds() {
  const reduced = useReducedMotion();
  const breath = useSharedValue(0);

  useEffect(() => {
    if (reduced) {
      return;
    }
    breath.set(
      withDelay(
        START_MS + ROUTE.length * STEP_MS + 900,
        withRepeat(withTiming(1, { duration: BREATH_MS, easing: Easing.linear }), -1),
      ),
    );
    return () => cancelAnimation(breath);
  }, [breath, reduced]);

  return (
    <View
      style={styles.wall}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {STARS.map((star, index) => (
        <Star key={index} {...star} index={index} />
      ))}
      {ROUTE.map((hold, index) => (
        <RouteHold key={index} hold={hold} index={index} breath={breath} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  wall: { flex: 1, minHeight: 240, marginBottom: 24 },
  slot: { position: 'absolute' },
  center: { alignItems: 'center', justifyContent: 'center' },
  bolt: { width: 7, height: 7, borderRadius: 4, opacity: 0.55 },
});
