import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { useTheme } from '@/hooks/use-theme';

interface ButtonProps {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'text';
  loading?: boolean;
  disabled?: boolean;
  icon?: ReactNode;
  /** 같은 버튼이 여럿일 때 무엇에 대한 것인지 읽어 준다. */
  accessibilityLabel?: string;
  /** 제공자 브랜드 색처럼 테마 밖의 색이 필요할 때만 쓴다. */
  colors?: { background: string; text: string; border?: string };
}

export function Button({
  label,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
  icon,
  accessibilityLabel = label,
  colors: override,
}: ButtonProps) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const scale = useSharedValue(1);
  const animated = useAnimatedStyle(() => ({ transform: [{ scale: scale.get() }] }));
  const inactive = loading || disabled;

  const background =
    override?.background ?? (variant === 'primary' ? colors.accent : 'transparent');
  const text = override?.text ?? (variant === 'primary' ? colors.onAccent : colors.text);
  const border = override?.border ?? (variant === 'secondary' ? colors.border : undefined);

  function pressTo(value: number) {
    if (!reduced) {
      scale.set(withSpring(value, motion.spring));
    }
  }

  function handlePress() {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onPress();
  }

  return (
    <Animated.View style={animated}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={{ disabled: inactive, busy: loading }}
        disabled={inactive}
        onPress={handlePress}
        onPressIn={() => pressTo(motion.pressScale)}
        onPressOut={() => pressTo(1)}
        style={[
          styles.base,
          {
            backgroundColor: background,
            borderRadius: radius.pill,
            paddingHorizontal: space(6),
            opacity: disabled && !loading ? 0.45 : 1,
          },
          border ? { borderWidth: 1.5, borderColor: border } : null,
        ]}
      >
        {loading ? (
          <PulseHolds color={text} />
        ) : (
          <>
            {icon ? <View style={styles.icon}>{icon}</View> : null}
            <AppText variant="label" style={{ color: text }}>
              {label}
            </AppText>
          </>
        )}
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 54,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  icon: { width: 22, alignItems: 'center' },
});
