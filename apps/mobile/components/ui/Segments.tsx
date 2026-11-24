import { useEffect, useState } from 'react';
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

interface SegmentsProps<T extends string> {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}

/** 고른 칸으로 노란 면이 미끄러져 간다. */
export function Segments<T extends string>({ options, value, onChange }: SegmentsProps<T>) {
  const { colors, motion, radius } = useTheme();
  const reduced = useReducedMotion();
  const [width, setWidth] = useState(0);
  const x = useSharedValue(0);
  const itemWidth = width / options.length;
  const target = itemWidth * options.findIndex((o) => o.value === value);

  useEffect(() => {
    x.set(reduced ? target : withSpring(target, motion.spring));
  }, [motion.spring, reduced, target, x]);

  const slide = useAnimatedStyle(() => ({ transform: [{ translateX: x.get() }] }));

  return (
    <View
      accessibilityRole="tablist"
      style={[styles.row, { backgroundColor: colors.surface, borderRadius: radius.pill }]}
    >
      <View style={styles.track} onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
        <Animated.View
          style={[
            styles.thumb,
            { width: itemWidth, backgroundColor: colors.accent, borderRadius: radius.pill },
            slide,
          ]}
        />
        {options.map((option) => {
          const selected = option.value === value;
          return (
            <Pressable
              key={option.value}
              accessibilityRole="tab"
              accessibilityLabel={option.label}
              accessibilityState={{ selected }}
              onPress={() => {
                if (!selected) {
                  void Haptics.selectionAsync().catch(() => {});
                  onChange(option.value);
                }
              }}
              style={styles.item}
            >
              <AppText
                variant="label"
                maxFontSizeMultiplier={1.4}
                tone={selected ? 'onAccent' : 'muted'}
              >
                {option.label}
              </AppText>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { padding: 4 },
  track: { flexDirection: 'row' },
  thumb: { position: 'absolute', top: 0, bottom: 0, left: 0 },
  item: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
});
