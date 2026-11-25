import { View } from 'react-native';

import Animated, { FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { MonthCalendar, type DayMark } from '@/components/meetup/MonthCalendar';
import { AppText } from '@/components/ui/AppText';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/hooks/use-theme';
import { formatDay, parseTime, type DateKey } from '@/lib/kst';
import { endsNextDay, type MeetupFormErrors, type Schedule } from '@/lib/meetup-form';

const NO_MARKS = new Map<DateKey, DayMark>();

interface ScheduleFieldsProps {
  value: Schedule;
  onChange: (change: Partial<Schedule>) => void;
  errors: MeetupFormErrors;
  editable: boolean;
}

export function ScheduleFields({ value, onChange, errors, editable }: ScheduleFieldsProps) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  return (
    <View style={{ gap: space(5) }}>
      <View
        style={{ gap: space(2), opacity: editable ? 1 : 0.45 }}
        pointerEvents={editable ? 'auto' : 'none'}
      >
        <AppText variant="label">날짜</AppText>
        <AppText accessibilityLiveRegion="polite">{formatDay(value.day)}</AppText>
        <MonthCalendar
          selected={value.day}
          marks={NO_MARKS}
          onSelect={(day) => onChange({ day })}
        />
      </View>
      <TextField
        label="시작 시각"
        value={value.start}
        onChangeText={(start) => onChange({ start })}
        keyboardType="number-pad"
        maxLength={5}
        editable={editable}
        error={errors.start}
        helper="서울 시각. 1930처럼 네 자리로 적어요."
      />
      <TextField
        label="종료 시각"
        value={value.end}
        onChangeText={(end) => onChange({ end })}
        keyboardType="number-pad"
        maxLength={5}
        editable={editable}
        error={errors.end}
      />
      {endsNextDay(value) ? (
        <Animated.View entering={reduced ? undefined : FadeInDown.springify().damping(14)}>
          <AppText accessibilityLiveRegion="polite">
            {`다음 날 ${parseTime(value.end)}에 끝나요.`}
          </AppText>
        </Animated.View>
      ) : null}
    </View>
  );
}
