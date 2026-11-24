import {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { useTheme } from '@/hooks/use-theme';

/** 누르는 동안 살짝 눌린다. style은 Animated.View에, handlers는 Pressable에 준다. */
export function usePressScale(pressed?: number) {
  const { motion } = useTheme();
  const reduced = useReducedMotion();
  const scale = useSharedValue(1);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: scale.get() }] }));

  function to(value: number) {
    if (!reduced) {
      scale.set(withSpring(value, motion.spring));
    }
  }

  return {
    style,
    handlers: { onPressIn: () => to(pressed ?? motion.pressScale), onPressOut: () => to(1) },
  };
}
