import { useEffect } from 'react';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
} from 'react-native-reanimated';

import { useTheme } from '@/hooks/use-theme';

interface HoldProps {
  on: boolean;
  size?: number;
  /** 여러 개가 이어질 때 켜지는 순서. */
  index?: number;
}

/** 벽에 박힌 홀드 하나. 켜지면 노란 면이 튀어나온다. */
export function Hold({ on, size = 14, index = 0 }: HoldProps) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const lit = useSharedValue(reduced && on ? 1 : 0);

  useEffect(() => {
    const target = on ? 1 : 0;
    lit.set(reduced ? target : withDelay(index * 40, withSpring(target, motion.spring)));
  }, [index, lit, motion.spring, on, reduced]);

  const animated = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, lit.get())),
    transform: [{ scale: 0.3 + 0.7 * lit.get() }, { rotate: `${-30 + 30 * lit.get()}deg` }],
  }));
  const shape = {
    width: size,
    height: size,
    borderTopLeftRadius: size * 0.5,
    borderTopRightRadius: size * 0.32,
    borderBottomRightRadius: size * 0.55,
    borderBottomLeftRadius: size * 0.42,
  };

  return (
    <Animated.View style={[shape, { backgroundColor: colors.holdOff }]}>
      <Animated.View style={[shape, { backgroundColor: colors.accent }, animated]} />
    </Animated.View>
  );
}
