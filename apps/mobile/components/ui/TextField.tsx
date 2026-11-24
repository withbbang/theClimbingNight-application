import { useEffect, useState } from 'react';
import { StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';

interface TextFieldProps extends Omit<TextInputProps, 'style'> {
  label: string;
  error?: string | null;
  helper?: string;
}

export function TextField({ label, error, helper, onFocus, onBlur, ...props }: TextFieldProps) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const [focused, setFocused] = useState(false);
  const shake = useSharedValue(0);
  const focus = useSharedValue(0);

  // 오류가 생기면 손이 미끄러지듯 좌우로 흔든다.
  useEffect(() => {
    if (error && !reduced) {
      shake.set(
        withSequence(
          withTiming(-9, { duration: 50 }),
          withTiming(8, { duration: 60 }),
          withTiming(-5, { duration: 60 }),
          withSpring(0, motion.spring),
        ),
      );
    }
  }, [error, motion.spring, reduced, shake]);

  useEffect(() => {
    const target = focused ? 1 : 0;
    focus.set(reduced ? target : withSpring(target, motion.spring));
  }, [focus, focused, motion.spring, reduced]);

  const shakeStyle = useAnimatedStyle(() => ({ transform: [{ translateX: shake.get() }] }));
  const ringStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, focus.get())),
    transform: [{ scale: 0.98 + 0.02 * focus.get() }],
  }));

  return (
    <View style={{ gap: space(2) }}>
      <AppText variant="label">{label}</AppText>
      <Animated.View style={shakeStyle}>
        <TextInput
          {...props}
          accessibilityLabel={label}
          placeholderTextColor={colors.textMuted}
          onFocus={(event) => {
            setFocused(true);
            onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            onBlur?.(event);
          }}
          style={[
            styles.input,
            props.multiline ? styles.multiline : null,
            {
              color: colors.text,
              backgroundColor: colors.surface,
              borderColor: error ? colors.danger : colors.border,
              borderRadius: radius.input,
              paddingHorizontal: space(4),
            },
          ]}
        />
        <Animated.View
          pointerEvents="none"
          style={[
            StyleSheet.absoluteFill,
            styles.ring,
            { borderColor: error ? colors.danger : colors.text, borderRadius: radius.input },
            ringStyle,
          ]}
        />
      </Animated.View>
      {error ? (
        <AppText
          variant="caption"
          tone="danger"
          accessibilityRole="alert"
          accessibilityLiveRegion="polite"
        >
          {error}
        </AppText>
      ) : helper ? (
        <AppText variant="caption" tone="muted">
          {helper}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  input: {
    minHeight: 54,
    borderWidth: 1,
    fontFamily: 'Pretendard-Regular',
    fontSize: 17,
    paddingVertical: 12,
  },
  multiline: { minHeight: 128, textAlignVertical: 'top' },
  ring: { borderWidth: 2 },
});
