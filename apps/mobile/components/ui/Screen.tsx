import type { ReactNode } from 'react';
import { KeyboardAvoidingView, ScrollView, StyleSheet, View } from 'react-native';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useTheme } from '@/hooks/use-theme';

interface ScreenProps {
  children: ReactNode;
  scroll?: boolean;
  /** 탭 화면은 탭 막대가 아래 여백을 맡는다. */
  bottomInset?: boolean;
}

export function Screen({ children, scroll = false, bottomInset = true }: ScreenProps) {
  const { colors, space } = useTheme();
  const insets = useSafeAreaInsets();
  const padding = {
    paddingTop: insets.top + space(4),
    // 직접 스크롤하는 탭 화면은 내용이 탭 막대 바로 위에서 잘리게 아래를 비우지 않는다.
    paddingBottom: bottomInset ? insets.bottom + space(5) : scroll ? space(5) : 0,
    paddingHorizontal: space(6),
  };

  return (
    <KeyboardAvoidingView
      // Android도 edge-to-edge라 창이 줄지 않으므로 직접 밀어 올린다.
      behavior="padding"
      style={[styles.fill, { backgroundColor: colors.background }]}
    >
      {scroll ? (
        <ScrollView
          contentContainerStyle={[styles.grow, padding]}
          keyboardShouldPersistTaps="handled"
        >
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.fill, padding]}>{children}</View>
      )}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 }, grow: { flexGrow: 1 } });
