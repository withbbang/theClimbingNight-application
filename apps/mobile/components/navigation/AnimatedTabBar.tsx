import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';

import { CalendarDotsIcon } from 'phosphor-react-native/src/icons/CalendarDots';
import { HouseIcon } from 'phosphor-react-native/src/icons/House';
import { ImagesSquareIcon } from 'phosphor-react-native/src/icons/ImagesSquare';
import { ToteIcon } from 'phosphor-react-native/src/icons/Tote';
import { UserIcon } from 'phosphor-react-native/src/icons/User';
import type { Icon } from 'phosphor-react-native';
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

const TABS: Record<string, { label: string; icon: Icon }> = {
  index: { label: '홈', icon: HouseIcon },
  meetups: { label: '벙', icon: CalendarDotsIcon },
  feed: { label: '피드', icon: ImagesSquareIcon },
  goods: { label: '굿즈', icon: ToteIcon },
  my: { label: '마이', icon: UserIcon },
};
const INDICATOR_WIDTH = 28;

/** 탭 막대가 쓰는 만큼만 좁힌 BottomTabBarProps. */
export interface AnimatedTabBarProps {
  state: { index: number; routes: { key: string; name: string }[] };
  emitter: {
    emit(event: { type: 'tabPress'; target: string; canPreventDefault: true }): {
      defaultPrevented: boolean;
    };
  };
  navigateToTab(routeKey: string): void;
  insets: { top: number; right: number; bottom: number; left: number };
}

interface TabItemProps {
  name: string;
  focused: boolean;
  onPress: () => void;
}

function TabItem({ name, focused, onPress }: TabItemProps) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const lift = useSharedValue(0);
  const { label, icon: TabIcon } = TABS[name];
  const color = focused ? colors.text : colors.textMuted;

  useEffect(() => {
    if (focused && !reduced) {
      lift.set(withSequence(withTiming(1, { duration: 110 }), withSpring(0, motion.spring)));
    }
  }, [focused, lift, motion.spring, reduced]);

  const animated = useAnimatedStyle(() => ({
    transform: [{ translateY: -6 * lift.get() }, { scale: 1 + 0.14 * lift.get() }],
  }));

  return (
    <Pressable
      accessibilityRole="tab"
      accessibilityLabel={label}
      accessibilityState={{ selected: focused }}
      onPress={onPress}
      style={styles.item}
    >
      <Animated.View style={animated}>
        <TabIcon size={26} color={color} weight={focused ? 'fill' : 'regular'} />
      </Animated.View>
      <AppText variant="caption" maxFontSizeMultiplier={1.4} style={{ color }}>
        {label}
      </AppText>
    </Pressable>
  );
}

export function AnimatedTabBar({ state, emitter, navigateToTab, insets }: AnimatedTabBarProps) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const [width, setWidth] = useState(0);
  const left = useSharedValue(0);
  const right = useSharedValue(INDICATOR_WIDTH);
  const tabWidth = width / state.routes.length;

  // 앞쪽 끝이 먼저 가고 뒤쪽 끝이 따라와, 막대가 손을 뻗듯 늘어난다.
  useEffect(() => {
    const target = tabWidth * state.index + (tabWidth - INDICATOR_WIDTH) / 2;
    if (reduced || width === 0) {
      left.set(target);
      right.set(target + INDICATOR_WIDTH);
      return;
    }
    const lead = { ...motion.spring, stiffness: motion.spring.stiffness * 1.6, damping: 24 };
    const trail = { ...motion.spring, stiffness: motion.spring.stiffness * 0.7 };
    const forward = target > left.get();
    left.set(withSpring(target, forward ? trail : lead));
    right.set(withSpring(target + INDICATOR_WIDTH, forward ? lead : trail));
  }, [left, motion.spring, reduced, right, state.index, tabWidth, width]);

  const indicator = useAnimatedStyle(() => {
    const span = Math.max(8, right.get() - left.get());
    const middle = (left.get() + right.get()) / 2;
    return {
      transform: [{ translateX: middle - INDICATOR_WIDTH / 2 }, { scaleX: span / INDICATOR_WIDTH }],
    };
  });

  return (
    <View
      accessibilityRole="tablist"
      onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
      style={[
        styles.bar,
        { backgroundColor: colors.surface, paddingBottom: Math.max(insets.bottom, 8) },
      ]}
    >
      <Animated.View
        style={[
          styles.indicator,
          { backgroundColor: colors.accent, opacity: width === 0 ? 0 : 1 },
          indicator,
        ]}
      />
      {state.routes.map((route, index) => {
        const focused = index === state.index;
        return (
          <TabItem
            key={route.key}
            name={route.name}
            focused={focused}
            onPress={() => {
              const event = emitter.emit({
                type: 'tabPress',
                target: route.key,
                canPreventDefault: true,
              });
              if (!focused && !event.defaultPrevented) {
                void Haptics.selectionAsync().catch(() => {});
                navigateToTab(route.key);
              }
            }}
          />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row' },
  item: {
    flex: 1,
    minHeight: 56,
    paddingTop: 10,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  indicator: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: INDICATOR_WIDTH,
    height: 4,
    borderBottomLeftRadius: 4,
    borderBottomRightRadius: 4,
  },
});
