import { useEffect } from 'react';
import type { ViewProps } from 'react-native';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
} from 'react-native-reanimated';

import { useTheme } from '@/hooks/use-theme';

const STAGGER_MS = 70;
const DISTANCE = 18;

interface FadeInItemProps extends ViewProps {
  /** 순서. 뒤에 있을수록 늦게 올라온다. */
  index: number;
}

export function FadeInItem({ index, style, ...props }: FadeInItemProps) {
  const { motion } = useTheme();
  const reduced = useReducedMotion();
  const progress = useSharedValue(reduced ? 1 : 0);

  useEffect(() => {
    if (!reduced) {
      progress.set(withDelay(index * STAGGER_MS, withSpring(1, motion.spring)));
    }
  }, [index, motion.spring, progress, reduced]);

  const animated = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, progress.get())),
    transform: [{ translateY: (1 - progress.get()) * DISTANCE }],
  }));

  return <Animated.View {...props} style={[style, animated]} />;
}
