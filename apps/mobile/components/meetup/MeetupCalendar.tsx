import { useState } from 'react';
import { ScrollView, View } from 'react-native';

import { MeetupCard } from '@/components/meetup/MeetupCard';
import { MonthCalendar, type DayMark } from '@/components/meetup/MonthCalendar';
import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { QueryState } from '@/components/ui/QueryState';
import { useMeetups } from '@/hooks/use-meetups';
import { useTheme } from '@/hooks/use-theme';
import { formatDay, monthEnd, monthStart, toDateKey, todayKst, type DateKey } from '@/lib/kst';
import type { MeetupSummaryResponse } from '@/types/api/meetup';

function toMarks(meetups: MeetupSummaryResponse[]) {
  const marks = new Map<DateKey, DayMark>();
  for (const meetup of meetups) {
    if (meetup.status === 'OPEN') {
      const day = toDateKey(meetup.startsAt);
      const mark = marks.get(day) ?? { count: 0, mine: false };
      marks.set(day, {
        count: mark.count + 1,
        mine: mark.mine || meetup.myStatus === 'CONFIRMED',
      });
    }
  }
  return marks;
}

export function MeetupCalendar() {
  const { space } = useTheme();
  const [selected, setSelected] = useState(() => todayKst());
  const query = useMeetups(monthStart(selected), monthEnd(selected));

  return (
    <ScrollView
      showsVerticalScrollIndicator={false}
      contentContainerStyle={{ gap: space(4), paddingBottom: space(6) }}
    >
      <MonthCalendar selected={selected} marks={toMarks(query.data ?? [])} onSelect={setSelected} />
      <QueryState query={query}>
        {(meetups) => {
          const ofDay = meetups.filter((m) => toDateKey(m.startsAt) === selected);
          return (
            <View key={selected} style={{ gap: space(2) }}>
              <FadeInItem index={0}>
                <AppText variant="label" accessibilityRole="header">
                  {formatDay(selected)}
                </AppText>
              </FadeInItem>
              {ofDay.length === 0 ? (
                <FadeInItem index={1}>
                  <AppText tone="muted">이날은 벙이 없어요</AppText>
                </FadeInItem>
              ) : (
                ofDay.map((meetup, i) => (
                  <MeetupCard key={meetup.id} meetup={meetup} index={i + 1} />
                ))
              )}
            </View>
          );
        }}
      </QueryState>
    </ScrollView>
  );
}
