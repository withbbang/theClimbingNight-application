import type { ReactNode } from 'react';
import { Pressable, StyleSheet } from 'react-native';

import Animated from 'react-native-reanimated';

import { FadeInItem } from '@/components/ui/FadeInItem';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';

const MAX_STAGGER = 8;

interface PressCardProps {
  label: string;
  index: number;
  onPress: () => void;
  children: ReactNode;
}

/** 목록에서 상세로 가는 칸. */
export function PressCard({ label, index, onPress, children }: PressCardProps) {
  const { colors, radius, space } = useTheme();
  const press = usePressScale();
  return (
    <FadeInItem index={Math.min(index, MAX_STAGGER)} style={{ marginBottom: space(3) }}>
      <Animated.View style={press.style}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={label}
          onPress={onPress}
          {...press.handlers}
          style={[
            styles.row,
            {
              gap: space(3),
              padding: space(4),
              borderRadius: radius.card,
              backgroundColor: colors.surface,
            },
          ]}
        >
          {children}
        </Pressable>
      </Animated.View>
    </FadeInItem>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 56, flexDirection: 'row', alignItems: 'center' },
});
