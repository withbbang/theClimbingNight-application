import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';

import { CaretLeftIcon } from 'phosphor-react-native/src/icons/CaretLeft';
import { CaretRightIcon } from 'phosphor-react-native/src/icons/CaretRight';
import Animated, {
  FadeInLeft,
  FadeInRight,
  useReducedMotion,
  ZoomIn,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Hold } from '@/components/ui/Hold';
import { usePressScale } from '@/hooks/use-press-scale';
import { useToday } from '@/hooks/use-now';
import { useTheme } from '@/hooks/use-theme';
import { addMonths, formatDay, monthGrid, monthStart, WEEKDAYS, type DateKey } from '@/lib/kst';

export interface DayMark {
  count: number;
  /** 내가 확정된 벙이 있는가. */
  mine: boolean;
}

interface DayCellProps {
  day: DateKey;
  mark: DayMark | undefined;
  today: boolean;
  selected: boolean;
  onPress: () => void;
}

function DayCell({ day, mark, today, selected, onPress }: DayCellProps) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.82);

  return (
    <Animated.View style={[styles.cell, press.style]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={mark ? `${formatDay(day)}, 벙 ${mark.count}개` : formatDay(day)}
        accessibilityState={{ selected }}
        onPress={onPress}
        {...press.handlers}
        style={styles.day}
      >
        {selected ? (
          <Animated.View
            entering={
              reduced
                ? undefined
                : ZoomIn.springify()
                    .damping(motion.spring.damping)
                    .stiffness(motion.spring.stiffness)
            }
            style={[styles.selected, { backgroundColor: colors.accent }]}
          />
        ) : null}
        <AppText
          variant={today || selected ? 'label' : 'body'}
          maxFontSizeMultiplier={1.4}
          tone={selected ? 'onAccent' : 'default'}
          style={today && !selected ? styles.today : null}
        >
          {Number(day.slice(8))}
        </AppText>
        <View style={styles.mark}>
          {mark ? <Hold on={mark.mine && !selected} size={8} /> : null}
        </View>
      </Pressable>
    </Animated.View>
  );
}

interface MonthCalendarProps {
  selected: DateKey;
  marks: Map<DateKey, DayMark>;
  onSelect: (day: DateKey) => void;
}

export function MonthCalendar({ selected, marks, onSelect }: MonthCalendarProps) {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  const today = useToday();
  const [step, setStep] = useState(0);
  const month = monthStart(selected);
  const [year, monthNumber] = month.split('-').map(Number);

  function move(by: 1 | -1) {
    void Haptics.selectionAsync().catch(() => {});
    setStep(by);
    onSelect(addMonths(month, by));
  }

  // 옮긴 방향에서 새 달이 들어온다.
  const entering =
    reduced || step === 0
      ? undefined
      : (step > 0 ? FadeInRight : FadeInLeft).springify().damping(20);

  return (
    <View style={{ gap: space(2) }}>
      <View style={styles.header}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="이전 달"
          onPress={() => move(-1)}
          style={styles.arrow}
        >
          <CaretLeftIcon size={22} color={colors.text} weight="bold" />
        </Pressable>
        <Animated.View key={month} entering={entering}>
          <AppText variant="title" accessibilityLiveRegion="polite">
            {`${year}년 ${monthNumber}월`}
          </AppText>
        </Animated.View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="다음 달"
          onPress={() => move(1)}
          style={styles.arrow}
        >
          <CaretRightIcon size={22} color={colors.text} weight="bold" />
        </Pressable>
      </View>
      <View
        style={styles.week}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {WEEKDAYS.map((name) => (
          <View key={name} style={styles.cell}>
            <AppText variant="caption" tone="muted" maxFontSizeMultiplier={1.4} style={styles.name}>
              {name}
            </AppText>
          </View>
        ))}
      </View>
      <Animated.View key={`grid-${month}`} entering={entering}>
        {monthGrid(month).map((week) => (
          <View key={week[0]} style={styles.week}>
            {week.map((day) =>
              monthStart(day) === month ? (
                <DayCell
                  key={day}
                  day={day}
                  today={day === today}
                  mark={marks.get(day)}
                  selected={day === selected}
                  onPress={() => onSelect(day)}
                />
              ) : (
                <View key={day} style={styles.cell} />
              ),
            )}
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  arrow: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  week: { flexDirection: 'row' },
  cell: { flex: 1 },
  name: { textAlign: 'center' },
  day: { minHeight: 52, alignItems: 'center', justifyContent: 'center' },
  selected: {
    position: 'absolute',
    width: 44,
    height: 44,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 15,
    borderBottomRightRadius: 24,
    borderBottomLeftRadius: 18,
  },
  today: { textDecorationLine: 'underline' },
  mark: { height: 10, justifyContent: 'center' },
});
