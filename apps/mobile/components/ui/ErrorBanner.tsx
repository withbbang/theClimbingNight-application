import { useEffect } from 'react';

import Animated, {
  FadeInDown,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';

/** 화면 단위 오류. 나타날 때 한 번 흔들린다. */
export function ErrorBanner({ message }: { message: string }) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const shake = useSharedValue(0);

  useEffect(() => {
    if (!reduced) {
      shake.set(
        withDelay(
          120,
          withSequence(
            withTiming(-8, { duration: 50 }),
            withTiming(7, { duration: 60 }),
            withTiming(-4, { duration: 60 }),
            withSpring(0, motion.spring),
          ),
        ),
      );
    }
  }, [message, motion.spring, reduced, shake]);

  const animated = useAnimatedStyle(() => ({ transform: [{ translateX: shake.get() }] }));

  return (
    <Animated.View entering={reduced ? undefined : FadeInDown.duration(220)}>
      <Animated.View
        style={[
          {
            borderWidth: 1.5,
            borderColor: colors.danger,
            borderRadius: radius.input,
            paddingVertical: space(3),
            paddingHorizontal: space(4),
            backgroundColor: colors.surface,
          },
          animated,
        ]}
      >
        <AppText tone="danger" accessibilityRole="alert" accessibilityLiveRegion="assertive">
          {message}
        </AppText>
      </Animated.View>
    </Animated.View>
  );
}
