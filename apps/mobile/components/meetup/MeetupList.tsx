import { FlashList } from '@shopify/flash-list';
import { CalendarDotsIcon } from 'phosphor-react-native/src/icons/CalendarDots';

import { MeetupCard } from '@/components/meetup/MeetupCard';
import { AppText } from '@/components/ui/AppText';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { useTheme } from '@/hooks/use-theme';
import { formatDay, toDateKey, type DateKey } from '@/lib/kst';
import { meetupPhase } from '@/lib/meetup-status';
import type { MeetupSummaryResponse } from '@/types/api/meetup';

type Row =
  { type: 'day'; day: DateKey } | { type: 'meetup'; meetup: MeetupSummaryResponse; index: number };

function toRows(meetups: MeetupSummaryResponse[]): Row[] {
  const now = new Date();
  const rows: Row[] = [];
  let lastDay: DateKey | null = null;
  meetups
    .filter((m) => meetupPhase(m, now) !== 'ENDED')
    .forEach((meetup, index) => {
      const day = toDateKey(meetup.startsAt);
      if (day !== lastDay) {
        rows.push({ type: 'day', day });
        lastDay = day;
      }
      rows.push({ type: 'meetup', meetup, index });
    });
  return rows;
}

interface MeetupListProps {
  meetups: MeetupSummaryResponse[];
  onRefresh: () => Promise<unknown>;
}

export function MeetupList({ meetups, onRefresh }: MeetupListProps) {
  const { space } = useTheme();

  return (
    <FlashList
      data={toRows(meetups)}
      keyExtractor={(row) => (row.type === 'day' ? row.day : String(row.meetup.id))}
      getItemType={(row) => row.type}
      renderItem={({ item }) =>
        item.type === 'day' ? (
          <AppText
            variant="label"
            accessibilityRole="header"
            style={{ paddingTop: space(3), paddingBottom: space(2) }}
          >
            {formatDay(item.day)}
          </AppText>
        ) : (
          <MeetupCard meetup={item.meetup} index={item.index} />
        )
      }
      ListEmptyComponent={
        <Notice
          illustration={<FloatingIcon icon={CalendarDotsIcon} />}
          title="예정된 벙이 없어요"
          description="새 벙이 열리면 이곳에서 바로 신청할 수 있어요."
        />
      }
      contentContainerStyle={{ paddingBottom: space(6) }}
      showsVerticalScrollIndicator={false}
      refreshControl={<PullToRefresh onRefresh={onRefresh} />}
    />
  );
}
