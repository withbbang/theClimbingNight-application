import { Pressable, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';

import Animated from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Hold } from '@/components/ui/Hold';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';

interface RadioRowProps {
  label: string;
  caption?: string | null;
  accessibilityLabel: string;
  selected: boolean;
  disabled?: boolean;
  onPress: () => void;
}

/** 여럿 중 하나. 고르면 홀드가 켜진다. */
export function RadioRow({
  label,
  caption,
  accessibilityLabel,
  selected,
  disabled = false,
  onPress,
}: RadioRowProps) {
  const { space } = useTheme();
  const press = usePressScale();
  return (
    <Animated.View style={press.style}>
      <Pressable
        accessibilityRole="radio"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={{ checked: selected, disabled }}
        disabled={disabled}
        onPress={() => {
          void Haptics.selectionAsync().catch(() => {});
          onPress();
        }}
        {...press.handlers}
        style={[styles.row, { gap: space(3) }]}
      >
        <Hold on={selected} size={20} />
        <View style={styles.fill}>
          <AppText variant="label">{label}</AppText>
          {caption ? (
            <AppText variant="caption" tone="muted">
              {caption}
            </AppText>
          ) : null}
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 48, flexDirection: 'row', alignItems: 'center' },
  fill: { flex: 1 },
});
