import { Pressable, StyleSheet } from 'react-native';

import { CaretRightIcon } from 'phosphor-react-native/src/icons/CaretRight';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useTheme } from '@/hooks/use-theme';

interface MenuRowProps {
  label: string;
  onPress: () => void;
  index: number;
}

/** 누르는 동안 화살표가 갈 곳을 가리킨다. */
export function MenuRow({ label, onPress, index }: MenuRowProps) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const pull = useSharedValue(0);
  const text = useAnimatedStyle(() => ({ transform: [{ translateX: 4 * pull.get() }] }));
  const arrow = useAnimatedStyle(() => ({ transform: [{ translateX: 8 * pull.get() }] }));

  function pullTo(value: number) {
    if (!reduced) {
      pull.set(withSpring(value, motion.spring));
    }
  }

  return (
    <FadeInItem index={index}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label}
        onPress={onPress}
        onPressIn={() => pullTo(1)}
        onPressOut={() => pullTo(0)}
        style={styles.row}
      >
        <Animated.View style={[styles.text, text]}>
          <AppText variant="label">{label}</AppText>
        </Animated.View>
        <Animated.View style={arrow}>
          <CaretRightIcon size={20} color={colors.textMuted} weight="bold" />
        </Animated.View>
      </Pressable>
    </FadeInItem>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 54, flexDirection: 'row', alignItems: 'center', paddingRight: 8 },
  text: { flex: 1 },
});
