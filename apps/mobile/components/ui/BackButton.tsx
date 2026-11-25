import { Pressable, StyleSheet } from 'react-native';

import { useRouter } from 'expo-router';

import { CaretLeftIcon } from 'phosphor-react-native/src/icons/CaretLeft';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { useTheme } from '@/hooks/use-theme';

interface BackButtonProps {
  /** 기본은 앞 화면, 없으면 홈. */
  onPress?: () => void;
}

/** 누르는 동안 화살표가 가려는 쪽으로 당겨진다. */
export function BackButton({ onPress }: BackButtonProps) {
  const router = useRouter();
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const pull = useSharedValue(0);
  const animated = useAnimatedStyle(() => ({ transform: [{ translateX: -6 * pull.get() }] }));

  function pullTo(value: number) {
    if (!reduced) {
      pull.set(withSpring(value, motion.spring));
    }
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="뒤로"
      onPress={onPress ?? (() => (router.canGoBack() ? router.back() : router.replace('/')))}
      onPressIn={() => pullTo(1)}
      onPressOut={() => pullTo(0)}
      style={styles.button}
    >
      <Animated.View style={animated}>
        <CaretLeftIcon size={26} color={colors.text} weight="bold" />
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 48,
    height: 48,
    marginLeft: -14,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
