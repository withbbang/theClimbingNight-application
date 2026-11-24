import { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';

const TRAVEL = 24;

interface ToggleProps {
  label: string;
  description?: string;
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
}

/** 켜면 홀드가 굴러가며 벽 색이 채워진다. */
export function Toggle({ label, description, value, onChange, disabled = false }: ToggleProps) {
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const on = useSharedValue(value ? 1 : 0);

  useEffect(() => {
    const target = value ? 1 : 0;
    on.set(reduced ? target : withSpring(target, motion.spring));
  }, [motion.spring, on, reduced, value]);

  const thumb = useAnimatedStyle(() => ({
    transform: [{ translateX: TRAVEL * on.get() }, { rotate: `${180 * on.get()}deg` }],
  }));
  const fill = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, on.get())) }));

  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityLabel={label}
      accessibilityHint={description}
      accessibilityState={{ checked: value, disabled }}
      disabled={disabled}
      onPress={() => {
        void Haptics.selectionAsync().catch(() => {});
        onChange(!value);
      }}
      style={[styles.row, { gap: space(4) }]}
    >
      <View style={styles.text}>
        <AppText variant="label">{label}</AppText>
        {description ? (
          <AppText variant="caption" tone="muted">
            {description}
          </AppText>
        ) : null}
      </View>
      <View style={[styles.track, { borderColor: colors.border }]}>
        <Animated.View style={[styles.fill, { backgroundColor: colors.accent }, fill]} />
        <Animated.View style={[styles.thumb, { backgroundColor: colors.text }, thumb]} />
        <Animated.View style={[styles.thumb, { backgroundColor: colors.onAccent }, thumb, fill]} />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 56, flexDirection: 'row', alignItems: 'center' },
  text: { flex: 1 },
  track: { width: 56, height: 32, borderRadius: 16, borderWidth: 1.5, overflow: 'hidden' },
  fill: { ...StyleSheet.absoluteFill },
  thumb: {
    position: 'absolute',
    top: 4.5,
    left: 4.5,
    width: 20,
    height: 20,
    borderTopLeftRadius: 10,
    borderTopRightRadius: 7,
    borderBottomRightRadius: 11,
    borderBottomLeftRadius: 8,
  },
});
