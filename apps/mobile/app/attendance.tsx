import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { CaretLeftIcon } from 'phosphor-react-native/src/icons/CaretLeft';
import { CaretRightIcon } from 'phosphor-react-native/src/icons/CaretRight';
import Animated, { FadeInLeft, FadeInRight, useReducedMotion } from 'react-native-reanimated';

import { RateWall } from '@/components/attendance/RateWall';
import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { Segments } from '@/components/ui/Segments';
import { useAttendanceRate } from '@/hooks/use-attendance-rate';
import { useTheme } from '@/hooks/use-theme';
import { todayKst } from '@/lib/kst';
import { shiftPeriod } from '@/lib/period';
import type { AttendancePeriod, AttendanceRateResponse } from '@/types/api/attendance';

const PERIODS = [
  { value: 'DAY', label: '일' },
  { value: 'WEEK', label: '주' },
  { value: 'MONTH', label: '월' },
  { value: 'QUARTER', label: '분기' },
  { value: 'YEAR', label: '연' },
] as const;

function rangeText({ from, to }: AttendanceRateResponse) {
  const [fromYear, fromMonth, fromDay] = from.split('-').map(Number);
  const [toYear, toMonth, toDay] = to.split('-').map(Number);
  const start = `${fromYear}년 ${fromMonth}월 ${fromDay}일`;
  if (from === to) {
    return start;
  }
  return `${start} ~ ${toYear === fromYear ? '' : `${toYear}년 `}${toMonth}월 ${toDay}일`;
}

function Attendance() {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  const [period, setPeriod] = useState<AttendancePeriod>('MONTH');
  const [date, setDate] = useState(() => todayKst());
  const [step, setStep] = useState<1 | -1>(1);
  const query = useAttendanceRate(period, date);
  const canGoNext = (query.data?.to ?? date) < todayKst();

  function move(by: 1 | -1) {
    setStep(by);
    setDate(shiftPeriod(period, date, by));
  }

  function change(next: AttendancePeriod) {
    setPeriod(next);
    setDate(todayKst());
  }

  return (
    <View style={{ gap: space(6) }}>
      <AppText variant="display" accessibilityRole="header">
        내 참석률
      </AppText>
      <Segments options={PERIODS} value={period} onChange={change} />
      <View style={styles.nav}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="이전 기간"
          onPress={() => move(-1)}
          style={styles.arrow}
        >
          <CaretLeftIcon size={22} color={colors.text} weight="bold" />
        </Pressable>
        <AppText variant="label" style={styles.range} accessibilityLiveRegion="polite">
          {query.data ? rangeText(query.data) : ''}
        </AppText>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="다음 기간"
          accessibilityState={{ disabled: !canGoNext }}
          disabled={!canGoNext}
          onPress={() => move(1)}
          style={[styles.arrow, canGoNext ? null : styles.disabled]}
        >
          <CaretRightIcon size={22} color={colors.text} weight="bold" />
        </Pressable>
      </View>
      <QueryState query={query}>
        {(data) => (
          <Animated.View
            key={`${period}-${date}`}
            entering={
              reduced ? undefined : (step > 0 ? FadeInRight : FadeInLeft).springify().damping(18)
            }
            style={{ gap: space(4) }}
          >
            {data.rate === null ? (
              <AppText variant="title">아직 기록된 벙이 없어요</AppText>
            ) : (
              <>
                <RateWall rate={data.rate} />
                <AppText variant="label">
                  {`${data.meetupCount}번 중 ${data.attendedCount}번 참석`}
                </AppText>
              </>
            )}
          </Animated.View>
        )}
      </QueryState>
    </View>
  );
}

export default function AttendanceScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="MEMBER">
        <Attendance />
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({
  nav: { flexDirection: 'row', alignItems: 'center' },
  arrow: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  disabled: { opacity: 0.3 },
  range: { flex: 1, textAlign: 'center' },
});
